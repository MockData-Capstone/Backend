package com.mockdata.zipfit.notification.service;

import com.mockdata.zipfit.notification.dto.NotificationResponse;
import com.mockdata.zipfit.notification.entity.PushSubscription;

/**
 * 웹 푸시 발송 (NOTI-01, P2). 실패하면 알림함에만 남기고 재전송하지 않는다.
 * 실제 발송(VAPID 서명, RFC 8291 암호화)은 캡스톤(2) 범위라 아직 구현하지 않았다.
 */
public interface WebPushSender {

	/** @return 구독이 더는 유효하지 않으면(404·410) false. 호출한 쪽이 구독을 지운다. */
	boolean send(PushSubscription subscription, NotificationResponse notification);
}
