package com.mockdata.zipfit.tour.service;

import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.minigame.entity.MiniGame;
import com.mockdata.zipfit.minigame.service.MiniGameService;
import com.mockdata.zipfit.tour.dto.IceServersResponse;
import com.mockdata.zipfit.tour.dto.TourHistoryResponse;
import com.mockdata.zipfit.tour.dto.TourJoinResponse;
import com.mockdata.zipfit.tour.dto.TourResponse;
import com.mockdata.zipfit.tour.entity.TourEnums.ConnectionState;
import com.mockdata.zipfit.tour.entity.TourEnums.EndReason;
import com.mockdata.zipfit.tour.entity.TourEnums.TourRole;
import com.mockdata.zipfit.tour.entity.TourEnums.TourStatus;
import com.mockdata.zipfit.tour.entity.TourParticipant;
import com.mockdata.zipfit.tour.entity.TourSession;
import com.mockdata.zipfit.tour.repository.TourParticipantRepository;
import com.mockdata.zipfit.tour.repository.TourSessionRepository;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOUR-01~06 실시간 투어 세션. 영상은 서버를 거치지 않고 참여자끼리 P2P(필요하면 TURN)로 주고받는다 (NFR-008).
 * 상태를 바꾸는 요청은 세션 행을 잠가 인원과 상태가 어긋나지 않게 한다.
 */
@Service
public class TourService {

	public static final String TOUR_STARTED = "TOUR_STARTED";
	public static final String TOUR_ENDED = "TOUR_ENDED";

	private final TourSessionRepository tourRepository;
	private final TourParticipantRepository participantRepository;
	private final MiniGameService gameService;
	private final WorkspaceAccess workspaceAccess;
	private final IceServerIssuer iceServerIssuer;
	private final ApplicationEventPublisher eventPublisher;
	private final int maxParticipants;
	private final Duration visitorRejoinTimeout;

	public TourService(
		TourSessionRepository tourRepository,
		TourParticipantRepository participantRepository,
		MiniGameService gameService,
		WorkspaceAccess workspaceAccess,
		IceServerIssuer iceServerIssuer,
		ApplicationEventPublisher eventPublisher,
		@Value("${tour.max-participants:6}") int maxParticipants,
		@Value("${tour.visitor-rejoin-timeout:3m}") Duration visitorRejoinTimeout
	) {
		this.tourRepository = tourRepository;
		this.participantRepository = participantRepository;
		this.gameService = gameService;
		this.workspaceAccess = workspaceAccess;
		this.iceServerIssuer = iceServerIssuer;
		this.eventPublisher = eventPublisher;
		this.maxParticipants = maxParticipants;
		this.visitorRejoinTimeout = visitorRejoinTimeout;
	}

	/**
	 * TOUR-01 가장 최근에 확정된 미니게임의 방문자만 시작할 수 있다. 진행 중인 투어는 Workspace당 1개다(제안).
	 * 시작하면 멤버에게 알린다.
	 */
	@Transactional
	public TourJoinResponse start(Long workspaceId, Long userId) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		MiniGame game = gameService.findLatestFinished(workspaceId)
			.filter(g -> userId.equals(g.getVisitorUserId()))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_SELECTED_VISITOR));
		if (tourRepository.findFirstByWorkspaceIdAndStatus(workspaceId, TourStatus.IN_PROGRESS).isPresent()) {
			throw new BusinessException(ErrorCode.TOUR_IN_PROGRESS);
		}
		if (tourRepository.existsByGameId(game.getId())) {
			throw new BusinessException(ErrorCode.TOUR_ALREADY_HELD);
		}
		TourSession tour;
		try {
			tour = tourRepository.saveAndFlush(
				new TourSession(member.getWorkspace(), game.getId(), userId, member.getUser().getNickname()));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.TOUR_IN_PROGRESS);
		}
		participantRepository.save(new TourParticipant(tour, userId, member.getUser().getNickname(), TourRole.VISITOR,
			Instant.now()));
		TourResponse response = toResponse(tour);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, TOUR_STARTED, response));
		return joinResponse(response, userId);
	}

	/** TOUR-02 진행 중인 투어에 시청자로 참여한다. 방문자 포함 최대 인원을 넘으면 막는다 (NFR-002). */
	@Transactional
	public TourJoinResponse join(Long tourId, Long userId) {
		TourSession tour = lockInProgress(tourId, userId);
		if (tour.isVisitor(userId)) {
			return rejoinVisitor(tour, userId);
		}
		Optional<TourParticipant> existing = participantRepository.findByTourIdAndUserId(tourId, userId);
		if (existing.map(TourParticipant::isActive).orElse(false)) {
			return joinResponse(toResponse(tour), userId);
		}
		if (participantRepository.countByTourIdAndLeftAtIsNull(tourId) >= maxParticipants) {
			throw new BusinessException(ErrorCode.TOUR_FULL);
		}
		Instant now = Instant.now();
		if (existing.isPresent()) {
			existing.get().rejoin(now);
		} else {
			WorkspaceMember member = workspaceAccess.requireMember(tour.getWorkspace().getId(), userId);
			participantRepository.save(new TourParticipant(tour, userId, member.getUser().getNickname(), TourRole.VIEWER, now));
		}
		participantRepository.flush();
		return joinResponse(toResponse(tour), userId);
	}

	/** TOUR-05 연결이 끊긴 방문자가 진행 중인 세션에 다시 들어온다. */
	@Transactional
	public TourJoinResponse rejoin(Long tourId, Long userId) {
		TourSession tour = lockInProgress(tourId, userId);
		if (!tour.isVisitor(userId)) {
			throw new BusinessException(ErrorCode.ONLY_VISITOR_ALLOWED);
		}
		return rejoinVisitor(tour, userId);
	}

	/** TOUR-04 시청자는 본인의 연결만 끝낸다. 세션은 유지된다. */
	@Transactional
	public void leave(Long tourId, Long userId) {
		TourSession tour = lockInProgress(tourId, userId);
		if (tour.isVisitor(userId)) {
			throw new BusinessException(ErrorCode.VISITOR_CANNOT_LEAVE);
		}
		TourParticipant participant = participantRepository.findByTourIdAndUserId(tourId, userId)
			.filter(TourParticipant::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_TOUR_PARTICIPANT));
		participant.leave(Instant.now());
		eventPublisher.publishEvent(new TourEvent(tourId, TourEvent.PARTICIPANT_REMOVED, userId));
	}

	/** TOUR-04 방문자가 종료하면 세션이 끝나고 모든 참여자의 연결을 정리한다 (SIG-05). */
	@Transactional
	public void end(Long tourId, Long userId) {
		TourSession tour = lockInProgress(tourId, userId);
		if (!tour.isVisitor(userId)) {
			throw new BusinessException(ErrorCode.ONLY_VISITOR_ALLOWED);
		}
		end(tour, EndReason.VISITOR_ENDED);
	}

	/** TOUR-02 보조. 참여 중인 사람에게만 STUN·TURN 정보를 준다. */
	@Transactional(readOnly = true)
	public IceServersResponse issueIceServers(Long tourId, Long userId) {
		requireActiveParticipant(tourId, userId);
		return iceServerIssuer.issue(userId);
	}

	/** TOUR-03 진행 중인 투어 */
	@Transactional(readOnly = true)
	public Optional<TourResponse> getCurrent(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return tourRepository.findFirstByWorkspaceIdAndStatus(workspaceId, TourStatus.IN_PROGRESS).map(this::toResponse);
	}

	/** TOUR-06 종료된 투어를 최신순으로 */
	@Transactional(readOnly = true)
	public List<TourHistoryResponse> getHistory(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return tourRepository.findAllByWorkspaceIdAndStatusOrderByIdDesc(workspaceId, TourStatus.ENDED).stream()
			.map(t -> new TourHistoryResponse(t.getId(), visitorOf(t), t.getStartedAt(), t.getEndedAt(), t.getEndReason()))
			.toList();
	}

	/** SIG-01 시그널링 접속 확인: 진행 중인 투어에 참여 중인 사람인지 */
	@Transactional(readOnly = true)
	public TourParticipant requireActiveParticipant(Long tourId, Long userId) {
		TourSession tour = tourRepository.findById(tourId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
		if (!tour.isInProgress()) {
			throw new BusinessException(ErrorCode.TOUR_NOT_IN_PROGRESS);
		}
		return participantRepository.findByTourIdAndUserId(tourId, userId)
			.filter(TourParticipant::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_TOUR_PARTICIPANT));
	}

	/** SIG-01 입장할 때 받는 방 정보 */
	@Transactional(readOnly = true)
	public TourResponse getTour(Long tourId) {
		return tourRepository.findById(tourId).map(this::toResponse)
			.orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
	}

	/**
	 * SIG-04 연결 상태 반영. 방문자가 끊기면 그 시각부터 재참여 대기 시간을 잰다.
	 * 진행 중이 아니거나 참여자가 아니면 무시한다.
	 */
	@Transactional
	public void reportConnectionState(Long tourId, Long userId, ConnectionState state) {
		tourRepository.findForUpdate(tourId).filter(TourSession::isInProgress).ifPresent(tour ->
			participantRepository.findByTourIdAndUserId(tourId, userId).filter(TourParticipant::isActive).ifPresent(p -> {
				p.changeState(state);
				if (tour.isVisitor(userId)) {
					if (state == ConnectionState.DISCONNECTED) {
						tour.visitorDisconnected(Instant.now());
					} else {
						tour.visitorReturned();
					}
				}
			}));
	}

	/** TOUR-05 방문자가 대기 시간 안에 돌아오지 않으면 세션을 끝낸다 (대기 시간은 미결, 기본 3분). */
	@Transactional
	public void endIfVisitorGone(Long tourId, Instant now) {
		tourRepository.findForUpdate(tourId)
			.filter(TourSession::isInProgress)
			.filter(t -> t.getVisitorDisconnectedAt() != null)
			.filter(t -> !t.getVisitorDisconnectedAt().plus(visitorRejoinTimeout).isAfter(now))
			.ifPresent(t -> end(t, EndReason.VISITOR_TIMEOUT));
	}

	@Transactional(readOnly = true)
	public List<Long> findToursWaitingForVisitor() {
		return tourRepository.findAllByStatusAndVisitorDisconnectedAtIsNotNull(TourStatus.IN_PROGRESS).stream()
			.map(TourSession::getId)
			.toList();
	}

	/** MEM-05·06 나가거나 내보내진 멤버는 투어에서도 뺀다. 방문자가 나가면 투어를 끝낸다. */
	@EventListener
	@Transactional
	public void onMemberRemoved(WorkspaceEvent event) {
		if (!WorkspaceEventTypes.MEMBER_LEFT.equals(event.type()) && !WorkspaceEventTypes.MEMBER_KICKED.equals(event.type())) {
			return;
		}
		Long userId = ((MemberService.MemberRemoved) event.data()).userId();
		tourRepository.findFirstByWorkspaceIdAndStatus(event.workspaceId(), TourStatus.IN_PROGRESS)
			.flatMap(t -> tourRepository.findForUpdate(t.getId()))
			.ifPresent(tour -> {
				if (tour.isVisitor(userId)) {
					end(tour, EndReason.VISITOR_LEFT_WORKSPACE);
					return;
				}
				participantRepository.findByTourIdAndUserId(tour.getId(), userId)
					.filter(TourParticipant::isActive)
					.ifPresent(p -> {
						p.leave(Instant.now());
						eventPublisher.publishEvent(new TourEvent(tour.getId(), TourEvent.PARTICIPANT_REMOVED, userId));
					});
			});
	}

	/** WS-04 진행 중인 투어는 종료를 알린 뒤 이력과 함께 지운다. */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		tourRepository.findFirstByWorkspaceIdAndStatus(event.workspaceId(), TourStatus.IN_PROGRESS)
			.ifPresent(tour -> eventPublisher.publishEvent(new TourEvent(tour.getId(), TourEvent.TOUR_ENDED,
				new TourEvent.TourEnded(tour.getId(), EndReason.WORKSPACE_DELETED))));
		participantRepository.deleteAllByWorkspaceId(event.workspaceId());
		tourRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	private TourJoinResponse rejoinVisitor(TourSession tour, Long userId) {
		participantRepository.findByTourIdAndUserId(tour.getId(), userId).ifPresent(p -> p.rejoin(Instant.now()));
		tour.visitorReturned();
		participantRepository.flush();
		return joinResponse(toResponse(tour), userId);
	}

	private void end(TourSession tour, EndReason reason) {
		Instant now = Instant.now();
		tour.end(reason, now);
		participantRepository.findAllByTourIdOrderByIdAsc(tour.getId()).stream()
			.filter(TourParticipant::isActive)
			.forEach(p -> p.leave(now));
		TourEvent.TourEnded ended = new TourEvent.TourEnded(tour.getId(), reason);
		eventPublisher.publishEvent(new TourEvent(tour.getId(), TourEvent.TOUR_ENDED, ended));
		eventPublisher.publishEvent(new WorkspaceEvent(tour.getWorkspace().getId(), TOUR_ENDED, ended));
	}

	private TourSession lockInProgress(Long tourId, Long userId) {
		TourSession tour = tourRepository.findForUpdate(tourId)
			.orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
		workspaceAccess.requireMember(tour.getWorkspace().getId(), userId);
		if (!tour.isInProgress()) {
			throw new BusinessException(ErrorCode.TOUR_NOT_IN_PROGRESS);
		}
		return tour;
	}

	private TourJoinResponse joinResponse(TourResponse tour, Long userId) {
		return new TourJoinResponse(tour, "/ws/signaling?tourId=" + tour.tourId(), iceServerIssuer.issue(userId));
	}

	private TourResponse toResponse(TourSession tour) {
		List<TourParticipant> active = participantRepository.findAllByTourIdOrderByIdAsc(tour.getId()).stream()
			.filter(TourParticipant::isActive)
			.toList();
		boolean visitorConnected = active.stream()
			.anyMatch(p -> tour.isVisitor(p.getUserId()) && p.getConnectionState() == ConnectionState.CONNECTED);
		return new TourResponse(
			tour.getId(),
			tour.getWorkspace().getId(),
			tour.getGameId(),
			tour.getStatus(),
			visitorOf(tour),
			visitorConnected,
			active.stream()
				.map(p -> new TourResponse.Participant(p.getUserId(), p.getNickname(), p.getRole(), p.getConnectionState(),
					p.getJoinedAt()))
				.toList(),
			active.size(),
			maxParticipants,
			tour.getStartedAt(),
			tour.getEndedAt(),
			tour.getEndReason()
		);
	}

	private static TourResponse.Visitor visitorOf(TourSession tour) {
		return new TourResponse.Visitor(tour.getVisitorUserId(), tour.getVisitorNickname());
	}
}
