package com.mockdata.zipfit.notification.service;

import com.mockdata.zipfit.notification.dto.NotificationResponse;
import com.mockdata.zipfit.notification.entity.PushSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시를 실제로 보내지 않고 로그만 남긴다.
 * TODO(NOTI-01, P2): VAPID 키를 발급하고 web-push 라이브러리로 실제 발송을 구현한다.
 */
@Slf4j
@Component
public class LoggingWebPushSender implements WebPushSender {

	@Override
	public boolean send(PushSubscription subscription, NotificationResponse notification) {
		log.info("[웹 푸시 미구현] userId={}, type={}, message={}",
			subscription.getUserId(), notification.type(), notification.message());
		return true;
	}
}
