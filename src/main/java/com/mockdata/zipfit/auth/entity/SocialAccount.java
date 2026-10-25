package com.mockdata.zipfit.auth.entity;

import com.mockdata.zipfit.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소셜 로그인 연동 정보. 회원은 제공자의 회원번호(카카오 회원번호, 구글 sub)로 식별한다. */
@Entity
@Table(
	name = "social_account",
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"provider", "provider_user_id"}),
		@UniqueConstraint(columnNames = {"user_id", "provider"})
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OAuthProvider provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	public SocialAccount(User user, OAuthProvider provider, String providerUserId) {
		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}
}
