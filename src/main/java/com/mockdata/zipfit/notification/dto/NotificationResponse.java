package com.mockdata.zipfit.notification.dto;

import com.mockdata.zipfit.notification.entity.Notification;
import com.mockdata.zipfit.notification.entity.NotificationType;
import java.time.Instant;

/**
 * 알림 (NOTI-02). 누르면 type에 따라 Workspace의 해당 화면으로 이동하고 읽음 처리한다.
 *
 * @param targetId   이동할 대상: VOTE_NEEDED는 후보지 ID, TOUR_STARTED는 투어 ID. 나머지는 null
 * @param count      묶인 건수 (새 메시지 알림만 1보다 크다)
 * @param linkActive 대상이 삭제됐으면 false. 링크를 비활성으로 표시한다 (제안)
 */
public record NotificationResponse(
	Long notificationId,
	NotificationType type,
	Long workspaceId,
	String workspaceName,
	String message,
	Long targetId,
	int count,
	boolean read,
	boolean linkActive,
	Instant createdAt,
	Instant updatedAt
) {

	public static NotificationResponse of(Notification n, boolean linkActive) {
		return new NotificationResponse(
			n.getId(), n.getType(), n.getWorkspaceId(), n.getWorkspaceName(), n.getMessage(), n.getTargetId(),
			n.getCount(), n.isRead(), linkActive, n.getCreatedAt(), n.getUpdatedAt()
		);
	}
}
