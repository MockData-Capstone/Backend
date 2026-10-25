package com.mockdata.zipfit.auth.entity;

import com.mockdata.zipfit.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원. 로그인 수단(카카오·구글·자체)은 {@link SocialAccount}로 분리해 한 회원에 여러 방식을 연동할 수 있게 한다(AUTH-08). */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	// 닉네임 길이 (제안)
	public static final int NICKNAME_MIN_LENGTH = 2;
	public static final int NICKNAME_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = NICKNAME_MAX_LENGTH)
	private String nickname;

	/** 소셜 제공자가 검증한 이메일만 저장한다. 선택 동의라 없을 수 있다. */
	private String email;

	/** null이면 프론트에서 기본 이미지를 보여준다. */
	@Column(length = 1000)
	private String profileImageUrl;

	@Column(nullable = false)
	private Instant privacyAgreedAt;

	public User(String nickname, String email, String profileImageUrl, Instant privacyAgreedAt) {
		this.nickname = nickname;
		this.email = email;
		this.profileImageUrl = profileImageUrl;
		this.privacyAgreedAt = privacyAgreedAt;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void resetProfileImage() {
		this.profileImageUrl = null;
	}

	public static boolean isValidNickname(String nickname) {
		if (nickname == null) {
			return false;
		}
		int length = nickname.trim().length();
		return length >= NICKNAME_MIN_LENGTH && length <= NICKNAME_MAX_LENGTH;
	}
}
