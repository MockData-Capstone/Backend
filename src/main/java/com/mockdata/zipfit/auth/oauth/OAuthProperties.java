package com.mockdata.zipfit.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Kakao kakao, Google google) {

	/**
	 * @param clientId     REST API 키
	 * @param clientSecret 카카오 로그인 > 보안 > Client Secret을 켰을 때만 필요하다.
	 * @param adminKey     탈퇴 시 연결 끊기(unlink)에 쓰는 어드민 키. 비어 있으면 연결 끊기를 건너뛴다.
	 */
	public record Kakao(String clientId, String clientSecret, String redirectUri, String adminKey) {
	}

	public record Google(String clientId, String clientSecret, String redirectUri) {
	}
}
