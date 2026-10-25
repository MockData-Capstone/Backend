package com.mockdata.zipfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizeUrlTest {

	private final OAuthProperties properties = new OAuthProperties(
		new OAuthProperties.Kakao("kakao-key", "", "http://localhost:5173/oauth/kakao/callback", ""),
		new OAuthProperties.Google("google-id", "google-secret", "http://localhost:5173/oauth/google/callback")
	);

	@Test
	void kakaoAuthorizeUrl() {
		String url = new KakaoOAuthClient(properties, null).authorizeUrl();

		assertThat(url).startsWith("https://kauth.kakao.com/oauth/authorize?")
			.contains("client_id=kakao-key")
			.contains("redirect_uri=http://localhost:5173/oauth/kakao/callback")
			.contains("response_type=code");
	}

	@Test
	void googleAuthorizeUrl() {
		String url = new GoogleOAuthClient(properties, null).authorizeUrl();

		assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
			.contains("client_id=google-id")
			.contains("scope=openid%20email%20profile");
	}
}
