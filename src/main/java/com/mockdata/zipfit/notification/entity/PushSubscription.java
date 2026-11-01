package com.mockdata.zipfit.notification.entity;

import com.mockdata.zipfit.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 웹 푸시 구독 (NOTI-04). 서비스 워커가 만든 endpoint가 기기를 나타낸다. */
@Entity
@Table(name = "push_subscription", indexes = @Index(columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

	public static final int ENDPOINT_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, unique = true, length = ENDPOINT_MAX_LENGTH)
	private String endpoint;

	@Column(nullable = false, length = 200)
	private String p256dh;

	@Column(nullable = false, length = 100)
	private String auth;

	public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
	}

	/** 같은 endpoint를 다시 등록하면 키를 갱신하고, 다른 회원의 것이었으면 지금 회원으로 옮긴다. */
	public void renew(Long userId, String p256dh, String auth) {
		this.userId = userId;
		this.p256dh = p256dh;
		this.auth = auth;
	}
}
