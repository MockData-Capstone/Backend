package com.mockdata.zipfit.auth.oauth;

import com.mockdata.zipfit.auth.entity.OAuthProvider;

public interface OAuthClient {

	OAuthProvider provider();

	/** 프론트가 이동할 인가 URL. 앱 키를 프론트에 두지 않기 위해 서버에서 만든다. */
	String authorizeUrl();

	/** 인가 코드를 토큰으로 교환하고 회원 정보를 조회한다. 실패하면 OAUTH_FAILED 예외를 던진다. */
	OAuthUserInfo fetchUserInfo(String code);

	/** 회원 탈퇴 시 제공자 쪽 연결을 끊는다(AUTH-06). 실패해도 탈퇴는 진행하므로 예외를 던지지 않는다. */
	default void unlink(String providerUserId) {
	}
}
