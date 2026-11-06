package com.mockdata.zipfit.notification.service;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.repository.CandidateRepository;
import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.notification.dto.NotificationDtos;
import com.mockdata.zipfit.notification.dto.NotificationResponse;
import com.mockdata.zipfit.notification.entity.Notification;
import com.mockdata.zipfit.notification.entity.NotificationType;
import com.mockdata.zipfit.notification.entity.PushSubscription;
import com.mockdata.zipfit.notification.repository.NotificationRepository;
import com.mockdata.zipfit.notification.repository.PushSubscriptionRepository;
import com.mockdata.zipfit.notification.socket.NotificationSessionRegistry;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** NOTI-01 알림 기록·전달, NOTI-02 알림함, NOTI-03 읽음 처리 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	public static final String NOTIFICATION_CREATED = "NOTIFICATION_CREATED";
	public static final String NOTIFICATION_READ = "NOTIFICATION_READ";
	/** 마지막 갱신 후 2주가 지난 알림은 보여주지 않는다 (추가 제안). */
	static final Duration RETENTION = Duration.ofDays(14);

	private final NotificationRepository notificationRepository;
	private final PushSubscriptionRepository subscriptionRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final WorkspaceRepository workspaceRepository;
	private final CandidateRepository candidateRepository;
	private final NotificationSessionRegistry sessionRegistry;
	private final WebPushSender webPushSender;

	/** NOTI-02 내 알림을 최신순으로. 나갔거나 내보내진 Workspace의 알림은 뺀다 (제안). */
	@Transactional(readOnly = true)
	public NotificationDtos.NotificationPage getPage(Long userId, String cursor, int size, boolean unreadOnly) {
		List<Long> workspaceIds = memberWorkspaceIds(userId);
		if (workspaceIds.isEmpty()) {
			return new NotificationDtos.NotificationPage(List.of(), null, false, 0);
		}
		Cursor at = Cursor.parse(cursor);
		List<Notification> rows = notificationRepository.findPage(userId, workspaceIds, since(), unreadOnly,
			at == null ? null : at.updatedAt(), at == null ? null : at.id(), PageRequest.ofSize(size + 1));
		boolean hasNext = rows.size() > size;
		List<Notification> page = hasNext ? rows.subList(0, size) : rows;
		return new NotificationDtos.NotificationPage(
			toResponses(page),
			hasNext ? Cursor.of(page.getLast()) : null,
			hasNext,
			notificationRepository.countUnread(userId, workspaceIds, since())
		);
	}

	/** NOTI-02 보조. 헤더 배지에 쓸 읽지 않은 개수 */
	@Transactional(readOnly = true)
	public long countUnread(Long userId) {
		List<Long> workspaceIds = memberWorkspaceIds(userId);
		return workspaceIds.isEmpty() ? 0 : notificationRepository.countUnread(userId, workspaceIds, since());
	}

	/** NOTI-03 한 건 읽음. 이미 읽은 알림은 그대로 둔다. 다른 탭·기기에 NOTIFICATION_READ로 알린다. */
	@Transactional
	public NotificationDtos.ReadResult read(Long userId, Long notificationId) {
		Notification notification = notificationRepository.findById(notificationId)
			.filter(n -> n.getUserId().equals(userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		boolean changed = notification.markRead();
		notificationRepository.flush();
		long unread = countUnread(userId);
		if (changed) {
			sessionRegistry.send(userId, WsMessage.of(NOTIFICATION_READ,
				new NotificationDtos.Read(List.of(notificationId), unread)));
		}
		return new NotificationDtos.ReadResult(notificationId, unread);
	}

	/** NOTI-03 전체 읽음. 본인의 알림만 바꾼다. */
	@Transactional
	public NotificationDtos.ReadAllResult readAll(Long userId) {
		List<Long> changed = notificationRepository.findAllByUserIdAndReadFalse(userId).stream()
			.filter(Notification::markRead)
			.map(Notification::getId)
			.toList();
		notificationRepository.flush();
		long unread = countUnread(userId);
		if (!changed.isEmpty()) {
			sessionRegistry.send(userId, WsMessage.of(NOTIFICATION_READ, new NotificationDtos.Read(changed, unread)));
		}
		return new NotificationDtos.ReadAllResult(changed.size(), unread);
	}

	/**
	 * NOTI-01 받는 사람마다 한 번만 기록한다. 원래 요청이 커밋된 뒤 불리므로 새 트랜잭션에서 저장한다.
	 * 새 메시지 알림은 Workspace별로 읽지 않은 1건에 묶어 개수와 시각만 갱신한다 (추가 제안).
	 *
	 * @param message 묶인 건수를 받아 문구를 만든다
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<Notification> record(
		Long workspaceId, NotificationType type, Collection<Long> recipientIds, IntFunction<String> message, Long targetId
	) {
		String workspaceName = workspaceRepository.findById(workspaceId).map(w -> w.getName()).orElse(null);
		if (workspaceName == null || recipientIds.isEmpty()) {
			return List.of();
		}
		// DB 시각 정밀도(마이크로초)에 맞춰 커서 비교가 어긋나지 않게 한다.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		List<Notification> saved = new ArrayList<>();
		for (Long userId : recipientIds) {
			Notification grouped = type == NotificationType.NEW_MESSAGE
				? notificationRepository.findFirstByUserIdAndWorkspaceIdAndTypeAndReadFalse(userId, workspaceId, type).orElse(null)
				: null;
			if (grouped != null) {
				grouped.addMessage(truncate(message.apply(grouped.getCount() + 1)), now);
				saved.add(grouped);
			} else {
				saved.add(notificationRepository.save(
					new Notification(userId, workspaceId, workspaceName, type, truncate(message.apply(1)), targetId, now)));
			}
		}
		return saved;
	}

	/**
	 * 기록한 알림을 전달한다. 알림 소켓에 접속 중이면 바로 보내고,
	 * 아니면 알림함에 남겨 두고 웹 푸시 구독이 있으면 푸시로도 보낸다(P2). 무효 구독은 지운다 (제안).
	 * 원래 요청이 커밋된 뒤 불리므로 새 트랜잭션에서 처리한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deliver(List<Notification> notifications) {
		List<NotificationResponse> responses = toResponses(notifications);
		for (NotificationResponse response : responses) {
			Long userId = notifications.stream()
				.filter(n -> n.getId().equals(response.notificationId()))
				.findFirst().orElseThrow().getUserId();
			if (sessionRegistry.isConnected(userId)) {
				sessionRegistry.send(userId, WsMessage.of(NOTIFICATION_CREATED,
					new NotificationDtos.Created(response, countUnread(userId))));
				continue;
			}
			for (PushSubscription subscription : subscriptionRepository.findAllByUserId(userId)) {
				if (!webPushSender.send(subscription, response)) {
					subscriptionRepository.delete(subscription);
				}
			}
		}
	}

	/** WS-04 Workspace 알림을 지운다. */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		notificationRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	/** AUTH-06 알림과 푸시 구독을 지운다. */
	@EventListener
	@Transactional
	public void onUserWithdrawing(UserWithdrawingEvent event) {
		notificationRepository.deleteAllByUserId(event.userId());
		subscriptionRepository.deleteAllByUserId(event.userId());
	}

	/** 삭제된 후보지를 가리키는 투표 알림은 링크를 비활성으로 표시한다 (제안). */
	private List<NotificationResponse> toResponses(List<Notification> notifications) {
		List<Long> candidateIds = notifications.stream()
			.filter(n -> n.getType() == NotificationType.VOTE_NEEDED && n.getTargetId() != null)
			.map(Notification::getTargetId)
			.toList();
		Set<Long> existingCandidates = candidateIds.isEmpty() ? Set.of()
			: candidateRepository.findAllById(candidateIds).stream().map(Candidate::getId).collect(Collectors.toSet());
		return notifications.stream()
			.map(n -> NotificationResponse.of(n,
				n.getType() != NotificationType.VOTE_NEEDED || existingCandidates.contains(n.getTargetId())))
			.toList();
	}

	private List<Long> memberWorkspaceIds(Long userId) {
		return memberRepository.findAllByUserId(userId).stream().map(m -> m.getWorkspace().getId()).toList();
	}

	private static Instant since() {
		return Instant.now().minus(RETENTION);
	}

	private static String truncate(String message) {
		return message.length() <= Notification.MESSAGE_MAX_LENGTH ? message
			: message.substring(0, Notification.MESSAGE_MAX_LENGTH);
	}

	/** 커서: "갱신 시각(ISO 8601)_ID" */
	record Cursor(Instant updatedAt, Long id) {

		static String of(Notification n) {
			return n.getUpdatedAt() + "_" + n.getId();
		}

		static Cursor parse(String cursor) {
			if (cursor == null || cursor.isBlank()) {
				return null;
			}
			try {
				String[] parts = cursor.split("_", 2);
				return new Cursor(Instant.parse(parts[0]), Long.valueOf(parts[1]));
			} catch (RuntimeException e) {
				throw new BusinessException(ErrorCode.INVALID_CURSOR);
			}
		}
	}
}
