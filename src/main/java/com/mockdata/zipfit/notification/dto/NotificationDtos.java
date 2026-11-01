package com.mockdata.zipfit.notification.dto;

import com.mockdata.zipfit.notification.entity.PushSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 알림 API의 작은 요청·응답들 */
public final class NotificationDtos {

	private NotificationDtos() {
	}

	/** NOTI-02 알림함. 커서 목록에 읽지 않은 개수를 더했다. */
	public record NotificationPage(List<NotificationResponse> items, String nextCursor, boolean hasNext, long unreadCount) {
	}

	public record UnreadCount(long unreadCount) {
	}

	/** NOTI-03 한 건 읽음 */
	public record ReadResult(Long notificationId, long unreadCount) {
	}

	/** NOTI-03 전체 읽음. updatedCount는 이번에 읽음으로 바뀐 개수다. */
	public record ReadAllResult(int updatedCount, long unreadCount) {
	}

	/**
	 * NOTI-04 서비스 워커의 PushSubscription.toJSON()을 그대로 보낸다.
	 * 예: { "endpoint": "https://...", "keys": { "p256dh": "...", "auth": "..." } }
	 */
	public record PushSubscribeRequest(
		@NotBlank @Size(max = PushSubscription.ENDPOINT_MAX_LENGTH) String endpoint,
		@NotNull @Valid Keys keys
	) {

		public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
		}
	}

	public record PushSubscriptionResponse(Long subscriptionId, String endpoint) {
	}

	/** NOTI-04 보조. 서버에 VAPID 키가 없으면 publicKey는 null이고 웹 푸시를 쓸 수 없다. */
	public record VapidPublicKey(String publicKey, boolean enabled) {
	}

	/** 알림 소켓 NOTIFICATION_CREATED */
	public record Created(NotificationResponse notification, long unreadCount) {
	}

	/** 알림 소켓 NOTIFICATION_READ: 다른 탭·기기의 배지를 맞춘다 */
	public record Read(List<Long> notificationIds, long unreadCount) {
	}
}
