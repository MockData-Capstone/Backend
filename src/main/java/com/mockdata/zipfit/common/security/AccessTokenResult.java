package com.mockdata.zipfit.common.security;

import java.time.Instant;

/** 접근 토큰 검증 결과. 만료는 따로 구분해 프론트가 재발급(AUTH-09)하게 한다. */
public sealed interface AccessTokenResult {

	/** expiresAt: WebSocket 연결을 토큰 만료 시각에 닫는 데 쓴다. */
	record Valid(Long userId, Instant expiresAt) implements AccessTokenResult {
	}

	record Expired() implements AccessTokenResult {
	}

	record Invalid() implements AccessTokenResult {
	}
}
