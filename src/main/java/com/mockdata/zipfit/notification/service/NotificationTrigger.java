package com.mockdata.zipfit.notification.service;

import com.mockdata.zipfit.candidate.dto.CandidateResponse;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.chat.dto.ChatMessageResponse;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.notification.entity.Notification;
import com.mockdata.zipfit.notification.entity.NotificationType;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import com.mockdata.zipfit.tour.dto.TourResponse;
import com.mockdata.zipfit.tour.service.TourService;
import com.mockdata.zipfit.workspace.dto.MemberResponse;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 Workspace 변경으로 알림을 만든다 (NOTI-01).
 * 행위를 한 본인에게는 보내지 않고(제안), 지금 멤버에게만 보낸다.
 * 알림이 실패해도 원래 요청에는 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTrigger {

	private final NotificationService notificationService;
	private final WorkspaceMemberRepository memberRepository;
	private final WorkspaceSessionRegistry workspaceSessions;

	@TransactionalEventListener(fallbackExecution = true)
	public void onWorkspaceEvent(WorkspaceEvent event) {
		try {
			switch (event.type()) {
				case WorkspaceEventTypes.MEMBER_JOINED -> {
					MemberResponse member = (MemberResponse) event.data();
					notify(event, NotificationType.MEMBER_JOINED, member.userId(),
						count -> "%s님이 들어왔습니다.".formatted(member.nickname()), null, false);
				}
				case ChatService.CHAT_MESSAGE -> {
					ChatMessageResponse message = (ChatMessageResponse) event.data();
					notify(event, NotificationType.NEW_MESSAGE, message.sender() == null ? null : message.sender().userId(),
						count -> "새 메시지가 %d개 있습니다.".formatted(count), null, true);
				}
				case RecommendationService.RECOMMENDATION_COMPLETED -> notify(event, NotificationType.ANALYSIS_COMPLETED, null,
					count -> "AI 분석이 끝났습니다. 추천 위치를 확인해보세요.", null, false);
				case CandidateService.CANDIDATE_ADDED -> {
					CandidateResponse candidate = (CandidateResponse) event.data();
					notify(event, NotificationType.VOTE_NEEDED, candidate.addedBy() == null ? null : candidate.addedBy().userId(),
						count -> "후보지가 추가되었습니다: %s. 투표해주세요.".formatted(candidate.name()), candidate.candidateId(), false);
				}
				case TourService.TOUR_STARTED -> {
					TourResponse tour = (TourResponse) event.data();
					notify(event, NotificationType.TOUR_STARTED, tour.visitor().userId(),
						count -> "%s님이 실시간 투어를 시작했습니다.".formatted(tour.visitor().nickname()), tour.tourId(), false);
				}
				default -> {
				}
			}
		} catch (RuntimeException e) {
			log.warn("알림 처리 실패: workspaceId={}, type={}", event.workspaceId(), event.type(), e);
		}
	}

	/**
	 * @param skipViewers true면 그 Workspace 화면(소켓)을 보고 있는 멤버에게는 보내지 않는다 (새 메시지, 제안)
	 */
	private void notify(
		WorkspaceEvent event, NotificationType type, Long actorId, IntFunction<String> message, Long targetId, boolean skipViewers
	) {
		List<Long> recipients = memberRepository.findUserIdsByWorkspaceId(event.workspaceId()).stream()
			.filter(userId -> !Objects.equals(userId, actorId))
			.filter(userId -> !skipViewers || !workspaceSessions.isConnected(event.workspaceId(), userId))
			.toList();
		List<Notification> saved = notificationService.record(event.workspaceId(), type, recipients, message, targetId);
		if (!saved.isEmpty()) {
			notificationService.deliver(saved);
		}
	}
}
