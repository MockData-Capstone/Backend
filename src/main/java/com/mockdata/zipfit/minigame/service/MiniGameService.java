package com.mockdata.zipfit.minigame.service;

import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.minigame.dto.GameEvents;
import com.mockdata.zipfit.minigame.dto.GameResponse;
import com.mockdata.zipfit.minigame.dto.StartGameRequest;
import com.mockdata.zipfit.minigame.entity.GameEnums.AbortReason;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameStatus;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameType;
import com.mockdata.zipfit.minigame.entity.GameEnums.Hand;
import com.mockdata.zipfit.minigame.entity.GameEnums.PlayerStatus;
import com.mockdata.zipfit.minigame.entity.GameEnums.VisitorRule;
import com.mockdata.zipfit.minigame.entity.MiniGame;
import com.mockdata.zipfit.minigame.entity.MiniGameChoice;
import com.mockdata.zipfit.minigame.entity.MiniGameParticipant;
import com.mockdata.zipfit.minigame.repository.MiniGameChoiceRepository;
import com.mockdata.zipfit.minigame.repository.MiniGameParticipantRepository;
import com.mockdata.zipfit.minigame.repository.MiniGameRepository;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GAME-01~06 방문자 선정 미니게임. 결과는 서버가 정하고, 진행은 Workspace 소켓으로 모든 멤버에게 동시에 알린다.
 * 상태를 바꾸는 요청은 게임 행을 잠가 동시에 와도 한 번만 처리한다.
 */
@Service
public class MiniGameService {

	private final MiniGameRepository gameRepository;
	private final MiniGameParticipantRepository participantRepository;
	private final MiniGameChoiceRepository choiceRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final WorkspaceAccess workspaceAccess;
	private final WorkspaceSessionRegistry sessionRegistry;
	private final ApplicationEventPublisher eventPublisher;
	private final Duration roundTimeout;

	public MiniGameService(
		MiniGameRepository gameRepository,
		MiniGameParticipantRepository participantRepository,
		MiniGameChoiceRepository choiceRepository,
		WorkspaceMemberRepository memberRepository,
		WorkspaceAccess workspaceAccess,
		WorkspaceSessionRegistry sessionRegistry,
		ApplicationEventPublisher eventPublisher,
		@Value("${game.round-timeout:10s}") Duration roundTimeout
	) {
		this.gameRepository = gameRepository;
		this.participantRepository = participantRepository;
		this.choiceRepository = choiceRepository;
		this.memberRepository = memberRepository;
		this.workspaceAccess = workspaceAccess;
		this.sessionRegistry = sessionRegistry;
		this.eventPublisher = eventPublisher;
		this.roundTimeout = roundTimeout;
	}

	/** GAME-01 참여 멤버는 지금 접속 중인 멤버 중 2명 이상이다. 진행 중인 게임이 있으면 시작할 수 없다(제안). */
	@Transactional
	public GameResponse start(Long workspaceId, Long userId, StartGameRequest request) {
		WorkspaceMember starter = workspaceAccess.requireMember(workspaceId, userId);
		if (request.type() == GameType.ROCK_PAPER_SCISSORS && request.visitorRule() == null) {
			throw new BusinessException(ErrorCode.VISITOR_RULE_REQUIRED);
		}
		List<Long> playerIds = request.participantUserIds().stream().distinct().toList();
		if (playerIds.size() < 2) {
			throw new BusinessException(ErrorCode.NOT_ENOUGH_PLAYERS);
		}
		Map<Long, WorkspaceMember> members = memberRepository.findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId)
			.stream()
			.collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity()));
		for (Long playerId : playerIds) {
			if (!members.containsKey(playerId) || !sessionRegistry.isConnected(workspaceId, playerId)) {
				throw new BusinessException(ErrorCode.PLAYER_NOT_AVAILABLE);
			}
		}
		if (gameRepository.findFirstByWorkspaceIdAndStatus(workspaceId, GameStatus.IN_PROGRESS).isPresent()) {
			throw new BusinessException(ErrorCode.GAME_IN_PROGRESS);
		}

		MiniGame game;
		try {
			game = gameRepository.saveAndFlush(new MiniGame(starter.getWorkspace(), request.type(), request.visitorRule(),
				userId, starter.getUser().getNickname()));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.GAME_IN_PROGRESS);
		}
		participantRepository.saveAll(playerIds.stream()
			.map(id -> new MiniGameParticipant(game, id, members.get(id).getUser().getNickname()))
			.toList());
		if (game.getType() == GameType.ROCK_PAPER_SCISSORS) {
			game.openRound(1, Instant.now().plus(roundTimeout));
		}
		GameResponse response = toResponse(game);
		publish(game, GameEvents.GAME_STARTED, response);
		if (game.getType() == GameType.ROCK_PAPER_SCISSORS) {
			publish(game, GameEvents.ROUND_OPENED,
				new GameEvents.RoundOpened(game.getId(), 1, playerIds, game.getRoundDeadline()));
		}
		return response;
	}

	/** GAME-02 룰렛: 남은 참가자 중 무작위로 방문자를 정한다. 시작과 분리해 회전 연출 전에 결과가 드러나지 않게 한다. */
	@Transactional
	public GameResponse draw(Long gameId, Long userId) {
		MiniGame game = lockInProgress(gameId, userId);
		if (game.getType() != GameType.ROULETTE) {
			throw new BusinessException(ErrorCode.WRONG_GAME_TYPE);
		}
		List<MiniGameParticipant> playing = playing(game);
		if (!game.getStartedByUserId().equals(userId) && playing.stream().noneMatch(p -> p.getUserId().equals(userId))) {
			throw new BusinessException(ErrorCode.NOT_GAME_PLAYER);
		}
		finish(game, playing.get(ThreadLocalRandom.current().nextInt(playing.size())));
		return toResponse(game);
	}

	/** GAME-02 가위바위보: 라운드 선택을 낸다. 남은 참가자가 모두 내면 판정한다. */
	@Transactional
	public GameResponse submit(Long gameId, int roundNo, Long userId, Hand hand) {
		MiniGame game = lockInProgress(gameId, userId);
		if (game.getType() != GameType.ROCK_PAPER_SCISSORS) {
			throw new BusinessException(ErrorCode.WRONG_GAME_TYPE);
		}
		List<MiniGameParticipant> playing = playing(game);
		if (playing.stream().noneMatch(p -> p.getUserId().equals(userId))) {
			throw new BusinessException(ErrorCode.NOT_GAME_PLAYER);
		}
		if (roundNo != game.getCurrentRound()) {
			throw new BusinessException(ErrorCode.ROUND_CLOSED);
		}
		if (choiceRepository.existsByGameIdAndRoundNoAndUserId(gameId, roundNo, userId)) {
			throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
		}
		choiceRepository.saveAndFlush(new MiniGameChoice(game, roundNo, userId, hand, false));
		resolveIfComplete(game, playing);
		return toResponse(game);
	}

	/** GAME-04 시작한 멤버만 중단할 수 있다(제안). 결과가 확정된 게임은 중단할 수 없다. */
	@Transactional
	public GameResponse abort(Long gameId, Long userId) {
		MiniGame game = lockInProgress(gameId, userId);
		if (!game.getStartedByUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.NOT_GAME_STARTER);
		}
		abort(game, AbortReason.STOPPED_BY_STARTER);
		return toResponse(game);
	}

	/** GAME-03 결과 조회. 확정 전에는 방문자가 null이다. */
	@Transactional(readOnly = true)
	public GameResponse get(Long gameId, Long userId) {
		MiniGame game = gameRepository.findById(gameId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
		workspaceAccess.requireMember(game.getWorkspace().getId(), userId);
		return toResponse(game);
	}

	/** GAME-05 진행 중인 게임. 새로고침해도 진행 상태를 이어서 볼 수 있게 한다. */
	@Transactional(readOnly = true)
	public Optional<GameResponse> getCurrent(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return gameRepository.findFirstByWorkspaceIdAndStatus(workspaceId, GameStatus.IN_PROGRESS).map(this::toResponse);
	}

	/** GAME-06 끝나거나 중단된 게임을 최신순으로 */
	@Transactional(readOnly = true)
	public List<GameResponse> getHistory(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return gameRepository.findAllByWorkspaceIdAndStatusInOrderByIdDesc(workspaceId,
				List.of(GameStatus.FINISHED, GameStatus.ABORTED)).stream()
			.map(this::toResponse)
			.toList();
	}

	/** 투어 시작(TOUR-01): 가장 최근에 확정된 게임 */
	@Transactional(readOnly = true)
	public Optional<MiniGame> findLatestFinished(Long workspaceId) {
		return gameRepository.findFirstByWorkspaceIdAndStatusOrderByIdDesc(workspaceId, GameStatus.FINISHED);
	}

	/** 라운드 마감이 지났으면 내지 않은 참가자 대신 무작위로 고르고 판정한다 (제안). */
	@Transactional
	public void closeRoundIfExpired(Long gameId, Instant now) {
		gameRepository.findForUpdate(gameId)
			.filter(MiniGame::isInProgress)
			.filter(game -> game.getRoundDeadline() != null && !game.getRoundDeadline().isAfter(now))
			.ifPresent(game -> {
				List<MiniGameParticipant> playing = playing(game);
				Set<Long> submitted = submittedUserIds(game);
				Hand[] hands = Hand.values();
				playing.stream()
					.filter(p -> !submitted.contains(p.getUserId()))
					.forEach(p -> choiceRepository.save(new MiniGameChoice(game, game.getCurrentRound(), p.getUserId(),
						hands[ThreadLocalRandom.current().nextInt(hands.length)], true)));
				choiceRepository.flush();
				resolveIfComplete(game, playing);
			});
	}

	/** 게임 도중 나가거나 연결이 끊긴 참가자를 결과에서 뺀다. 2명 미만이 되면 자동으로 중단한다 (제안). */
	@Transactional
	public void removePlayer(Long gameId, Long userId) {
		gameRepository.findForUpdate(gameId).filter(MiniGame::isInProgress).ifPresent(game -> {
			List<MiniGameParticipant> playing = playing(game);
			Optional<MiniGameParticipant> leaving = playing.stream().filter(p -> p.getUserId().equals(userId)).findFirst();
			if (leaving.isEmpty()) {
				return;
			}
			leaving.get().leave(game.getCurrentRound());
			publish(game, GameEvents.GAME_PLAYER_LEFT,
				new GameEvents.PlayerLeft(game.getId(), userId, leaving.get().getNickname()));
			List<MiniGameParticipant> remaining = playing.stream().filter(MiniGameParticipant::isPlaying).toList();
			if (remaining.size() < 2) {
				abort(game, AbortReason.NOT_ENOUGH_PLAYERS);
			} else if (game.getType() == GameType.ROCK_PAPER_SCISSORS) {
				resolveIfComplete(game, remaining);
			}
		});
	}

	@Transactional(readOnly = true)
	public List<MiniGame> findInProgress() {
		return gameRepository.findAllByStatus(GameStatus.IN_PROGRESS);
	}

	@Transactional(readOnly = true)
	public List<Long> findPlayingUserIds(Long gameId) {
		return participantRepository.findAllByGameIdOrderByIdAsc(gameId).stream()
			.filter(MiniGameParticipant::isPlaying)
			.map(MiniGameParticipant::getUserId)
			.toList();
	}

	/** MEM-05·06 나가거나 내보내진 멤버는 진행 중인 게임에서 뺀다. */
	@EventListener
	@Transactional
	public void onMemberRemoved(WorkspaceEvent event) {
		if (WorkspaceEventTypes.MEMBER_LEFT.equals(event.type()) || WorkspaceEventTypes.MEMBER_KICKED.equals(event.type())) {
			Long userId = ((MemberService.MemberRemoved) event.data()).userId();
			gameRepository.findFirstByWorkspaceIdAndStatus(event.workspaceId(), GameStatus.IN_PROGRESS)
				.ifPresent(game -> removePlayer(game.getId(), userId));
		}
	}

	/** WS-04 미니게임 이력도 함께 지운다. */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		choiceRepository.deleteAllByWorkspaceId(event.workspaceId());
		participantRepository.deleteAllByWorkspaceId(event.workspaceId());
		gameRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	/**
	 * 남은 참가자가 모두 냈으면 판정한다 (제안).
	 * 한 가지만 나왔거나 세 가지가 모두 나오면 무승부로 같은 멤버가 다시 한다.
	 * 아니면 방문자 기준(승자/패자)에 해당하는 쪽만 남기고, 1명이 남으면 그 멤버가 방문자다.
	 */
	private void resolveIfComplete(MiniGame game, List<MiniGameParticipant> playing) {
		int roundNo = game.getCurrentRound();
		Map<Long, MiniGameChoice> choices = choiceRepository.findAllByGameIdAndRoundNo(game.getId(), roundNo).stream()
			.collect(Collectors.toMap(MiniGameChoice::getUserId, Function.identity()));
		List<MiniGameParticipant> active = playing.stream().filter(MiniGameParticipant::isPlaying).toList();
		if (!active.stream().allMatch(p -> choices.containsKey(p.getUserId()))) {
			return;
		}
		Set<Hand> hands = active.stream().map(p -> choices.get(p.getUserId()).getHand()).collect(Collectors.toSet());
		List<MiniGameParticipant> survivors = hands.size() == 2 ? survivorsOf(game, active, choices, hands) : active;
		active.stream().filter(p -> !survivors.contains(p)).forEach(p -> p.eliminate(roundNo));
		List<Long> survivorIds = survivors.stream().map(MiniGameParticipant::getUserId).toList();
		publish(game, GameEvents.ROUND_RESULT, new GameEvents.RoundResult(game.getId(),
			toRound(roundNo, choices.values().stream().toList(), participantsById(game)), survivorIds));
		if (survivors.size() == 1) {
			finish(game, survivors.getFirst());
		} else {
			game.openRound(roundNo + 1, Instant.now().plus(roundTimeout));
			publish(game, GameEvents.ROUND_OPENED,
				new GameEvents.RoundOpened(game.getId(), roundNo + 1, survivorIds, game.getRoundDeadline()));
		}
	}

	/** 두 가지가 나온 라운드에서 방문자 기준(승자/패자)에 해당하는 쪽 */
	private static List<MiniGameParticipant> survivorsOf(
		MiniGame game, List<MiniGameParticipant> active, Map<Long, MiniGameChoice> choices, Set<Hand> hands
	) {
		Hand[] pair = hands.toArray(Hand[]::new);
		Hand winning = pair[0].beats(pair[1]) ? pair[0] : pair[1];
		boolean keepWinners = game.getVisitorRule() == VisitorRule.WINNER;
		return active.stream()
			.filter(p -> (choices.get(p.getUserId()).getHand() == winning) == keepWinners)
			.toList();
	}

	private void finish(MiniGame game, MiniGameParticipant visitor) {
		game.finish(visitor, Instant.now());
		publish(game, GameEvents.GAME_FINISHED, new GameEvents.GameFinished(game.getId(),
			new GameResponse.Player(visitor.getUserId(), visitor.getNickname())));
	}

	private void abort(MiniGame game, AbortReason reason) {
		game.abort(reason, Instant.now());
		publish(game, GameEvents.GAME_ABORTED, new GameEvents.GameAborted(game.getId(), reason));
	}

	private MiniGame lockInProgress(Long gameId, Long userId) {
		MiniGame game = gameRepository.findForUpdate(gameId)
			.orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
		workspaceAccess.requireMember(game.getWorkspace().getId(), userId);
		if (!game.isInProgress()) {
			throw new BusinessException(ErrorCode.GAME_NOT_IN_PROGRESS);
		}
		return game;
	}

	private List<MiniGameParticipant> playing(MiniGame game) {
		return participantRepository.findAllByGameIdOrderByIdAsc(game.getId()).stream()
			.filter(MiniGameParticipant::isPlaying)
			.toList();
	}

	private Set<Long> submittedUserIds(MiniGame game) {
		return choiceRepository.findAllByGameIdAndRoundNo(game.getId(), game.getCurrentRound()).stream()
			.map(MiniGameChoice::getUserId)
			.collect(Collectors.toSet());
	}

	private Map<Long, MiniGameParticipant> participantsById(MiniGame game) {
		return participantRepository.findAllByGameIdOrderByIdAsc(game.getId()).stream()
			.collect(Collectors.toMap(MiniGameParticipant::getUserId, Function.identity()));
	}

	private void publish(MiniGame game, String type, Object data) {
		eventPublisher.publishEvent(new WorkspaceEvent(game.getWorkspace().getId(), type, data));
	}

	private GameResponse toResponse(MiniGame game) {
		Map<Long, MiniGameParticipant> participants = participantsById(game);
		List<MiniGameChoice> choices = choiceRepository.findAllByGameIdOrderByRoundNoAscIdAsc(game.getId());
		// 진행 중인 라운드에서 무엇을 냈는지는 판정이 끝나야 공개한다.
		int lastResolvedRound = game.getStatus() == GameStatus.FINISHED ? game.getCurrentRound() : game.getCurrentRound() - 1;
		List<GameResponse.Round> rounds = choices.stream()
			.filter(c -> c.getRoundNo() <= lastResolvedRound)
			.collect(Collectors.groupingBy(MiniGameChoice::getRoundNo))
			.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> toRound(entry.getKey(), entry.getValue(), participants))
			.toList();
		List<Long> submitted = game.isInProgress()
			? choices.stream().filter(c -> c.getRoundNo() == game.getCurrentRound()).map(MiniGameChoice::getUserId).toList()
			: List.of();
		return new GameResponse(
			game.getId(),
			game.getWorkspace().getId(),
			game.getType(),
			game.getVisitorRule(),
			game.getStatus(),
			new GameResponse.Player(game.getStartedByUserId(), game.getStartedByNickname()),
			participants.values().stream()
				.map(p -> new GameResponse.Participant(p.getUserId(), p.getNickname(), p.getStatus(), p.getOutRound()))
				.toList(),
			game.getCurrentRound(),
			game.getRoundDeadline(),
			submitted,
			rounds,
			game.getVisitorUserId() == null ? null : new GameResponse.Player(game.getVisitorUserId(), game.getVisitorNickname()),
			game.getAbortReason(),
			game.getStartedAt(),
			game.getFinishedAt()
		);
	}

	private static GameResponse.Round toRound(int roundNo, List<MiniGameChoice> choices, Map<Long, MiniGameParticipant> participants) {
		List<Long> eliminated = participants.values().stream()
			.filter(p -> p.getStatus() == PlayerStatus.ELIMINATED)
			.filter(p -> p.getOutRound() != null && p.getOutRound() == roundNo)
			.map(MiniGameParticipant::getUserId)
			.toList();
		return new GameResponse.Round(
			roundNo,
			eliminated.isEmpty(),
			choices.stream()
				.map(c -> new GameResponse.Choice(c.getUserId(), participants.containsKey(c.getUserId())
					? participants.get(c.getUserId()).getNickname() : null, c.getHand(), c.isAuto()))
				.toList(),
			eliminated
		);
	}
}
