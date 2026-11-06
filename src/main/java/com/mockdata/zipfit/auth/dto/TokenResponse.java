package com.mockdata.zipfit.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 발급한 토큰. 재발급 토큰은 응답 본문(JSON)에 넣지 않고 httpOnly 쿠키로만 준다 (RefreshTokenCookie).
 *
 * @param accessTokenExpiresIn 접근 토큰 수명(초)
 */
public record TokenResponse(
	String accessToken,
	@JsonIgnore String refreshToken,
	long accessTokenExpiresIn,
	String tokenType
) {

	public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
		this(accessToken, refreshToken, accessTokenExpiresIn, "Bearer");
	}
}
