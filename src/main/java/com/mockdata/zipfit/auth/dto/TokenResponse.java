package com.mockdata.zipfit.auth.dto;

/**
 * @param accessTokenExpiresIn 접근 토큰 수명(초)
 */
public record TokenResponse(
	String accessToken,
	String refreshToken,
	long accessTokenExpiresIn,
	String tokenType
) {

	public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
		this(accessToken, refreshToken, accessTokenExpiresIn, "Bearer");
	}
}
