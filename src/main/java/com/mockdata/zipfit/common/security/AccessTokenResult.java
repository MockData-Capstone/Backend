package com.mockdata.zipfit.common.security;

/** 접근 토큰 검증 결과. 만료는 따로 구분해 프론트가 재발급(AUTH-09)하게 한다. */
public sealed interface AccessTokenResult {

	record Valid(Long userId) implements AccessTokenResult {
	}

	record Expired() implements AccessTokenResult {
	}

	record Invalid() implements AccessTokenResult {
	}
}
