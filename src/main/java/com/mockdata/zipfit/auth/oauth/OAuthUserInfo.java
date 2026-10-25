package com.mockdata.zipfit.auth.oauth;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.User;

/** 소셜 제공자에서 받아온 회원 정보. 이메일은 제공자가 검증한 경우에만 채운다. */
public record OAuthUserInfo(
	OAuthProvider provider,
	String providerUserId,
	String email,
	String nickname,
	String profileImageUrl
) {

	/** 자체 가입 회원과 비교할 수 있게 이메일을 소문자로 맞춘다. */
	public OAuthUserInfo withNormalizedEmail() {
		return new OAuthUserInfo(provider, providerUserId, User.normalizeEmail(email), nickname, profileImageUrl);
	}
}
