package com.mockdata.zipfit.notification.service;

import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.notification.dto.NotificationDtos;
import com.mockdata.zipfit.notification.entity.PushSubscription;
import com.mockdata.zipfit.notification.repository.PushSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** NOTI-04·05 웹 푸시 구독 (P2) */
@Service
public class PushSubscriptionService {

	private final PushSubscriptionRepository subscriptionRepository;
	private final String vapidPublicKey;

	public PushSubscriptionService(
		PushSubscriptionRepository subscriptionRepository,
		@Value("${webpush.vapid-public-key:}") String vapidPublicKey
	) {
		this.subscriptionRepository = subscriptionRepository;
		this.vapidPublicKey = vapidPublicKey;
	}

	/** 키를 프론트 빌드에 고정하지 않고 서버 키와 맞추기 위해 서버가 준다. */
	public NotificationDtos.VapidPublicKey getVapidPublicKey() {
		return vapidPublicKey.isBlank()
			? new NotificationDtos.VapidPublicKey(null, false)
			: new NotificationDtos.VapidPublicKey(vapidPublicKey, true);
	}

	/** 회원·기기 단위로 저장한다. 같은 endpoint가 있으면 갱신하고, 다른 회원의 것이면 지금 회원으로 옮긴다. */
	@Transactional
	public NotificationDtos.PushSubscriptionResponse subscribe(Long userId, NotificationDtos.PushSubscribeRequest request) {
		PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
			.map(existing -> {
				existing.renew(userId, request.keys().p256dh(), request.keys().auth());
				return existing;
			})
			.orElseGet(() -> subscriptionRepository.save(new PushSubscription(
				userId, request.endpoint(), request.keys().p256dh(), request.keys().auth())));
		return new NotificationDtos.PushSubscriptionResponse(subscription.getId(), subscription.getEndpoint());
	}

	/** 본인 구독만 해제할 수 있다. 앱 안 알림은 계속 받는다. */
	@Transactional
	public void unsubscribe(Long userId, String endpoint) {
		PushSubscription subscription = subscriptionRepository.findByEndpoint(endpoint)
			.filter(s -> s.getUserId().equals(userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_NOT_FOUND));
		subscriptionRepository.delete(subscription);
	}
}
