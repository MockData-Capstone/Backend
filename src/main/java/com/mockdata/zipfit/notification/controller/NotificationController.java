package com.mockdata.zipfit.notification.controller;

import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.notification.dto.NotificationDtos;
import com.mockdata.zipfit.notification.service.NotificationService;
import com.mockdata.zipfit.notification.service.PushSubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 새 알림은 알림 소켓(/ws/notifications)의 NOTIFICATION_CREATED로 받는다. */
@RestController
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final PushSubscriptionService pushSubscriptionService;

	/** NOTI-02 알림함 (최신순 커서 페이지 + 읽지 않은 개수) */
	@GetMapping("/api/notifications")
	public NotificationDtos.NotificationPage getNotifications(
		@CurrentUser Long userId,
		@RequestParam(required = false) String cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(defaultValue = "false") boolean unreadOnly
	) {
		return notificationService.getPage(userId, cursor, size, unreadOnly);
	}

	/** NOTI-02 보조 읽지 않은 알림 개수 */
	@GetMapping("/api/notifications/unread-count")
	public NotificationDtos.UnreadCount getUnreadCount(@CurrentUser Long userId) {
		return new NotificationDtos.UnreadCount(notificationService.countUnread(userId));
	}

	/** NOTI-03 전체 읽음 */
	@PatchMapping("/api/notifications/read-all")
	public NotificationDtos.ReadAllResult readAll(@CurrentUser Long userId) {
		return notificationService.readAll(userId);
	}

	/** NOTI-03 알림 읽음 */
	@PatchMapping("/api/notifications/{notificationId}/read")
	public NotificationDtos.ReadResult read(@CurrentUser Long userId, @PathVariable Long notificationId) {
		return notificationService.read(userId, notificationId);
	}

	/** NOTI-04 보조 VAPID 공개키 */
	@GetMapping("/api/push-subscriptions/vapid-public-key")
	public NotificationDtos.VapidPublicKey getVapidPublicKey() {
		return pushSubscriptionService.getVapidPublicKey();
	}

	/** NOTI-04 웹 푸시 구독 등록 (같은 endpoint면 갱신) */
	@PostMapping("/api/push-subscriptions")
	@ResponseStatus(HttpStatus.CREATED)
	public NotificationDtos.PushSubscriptionResponse subscribe(
		@CurrentUser Long userId,
		@Valid @RequestBody NotificationDtos.PushSubscribeRequest request
	) {
		return pushSubscriptionService.subscribe(userId, request);
	}

	/** NOTI-05 웹 푸시 구독 해제 (본인 구독만) */
	@DeleteMapping("/api/push-subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsubscribe(@CurrentUser Long userId, @RequestParam @NotBlank String endpoint) {
		pushSubscriptionService.unsubscribe(userId, endpoint);
	}
}
