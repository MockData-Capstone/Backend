package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.common.security.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 재발급 토큰을 httpOnly 쿠키로 주고받는다 (AUTH-09).
 * 자바스크립트가 읽을 수 없어 XSS로 훔쳐 가기 어렵고, 새로고침해도 /api/auth/token/refresh로 로그인을 이어 간다.
 * 재발급·로그아웃에만 필요하므로 경로를 /api/auth로 좁혀 다른 API 요청에는 실리지 않게 한다.
 * <ul>
 *   <li>로컬(http://localhost:5173 ↔ :8080)은 같은 사이트라 SameSite=Lax, Secure 없이 동작한다.</li>
 *   <li>프론트와 백엔드 도메인이 다르게 배포되면 auth.cookie.same-site=None, auth.cookie.secure=true(HTTPS 필수)로 바꾼다.</li>
 * </ul>
 */
@Component
public class RefreshTokenCookie {

	public static final String NAME = "refresh_token";
	static final String PATH = "/api/auth";

	private final Duration maxAge;
	private final boolean secure;
	private final String sameSite;
	private final String domain;

	public RefreshTokenCookie(
		JwtProperties jwtProperties,
		@Value("${auth.cookie.secure:false}") boolean secure,
		@Value("${auth.cookie.same-site:Lax}") String sameSite,
		@Value("${auth.cookie.domain:}") String domain
	) {
		this.maxAge = jwtProperties.refreshTokenExpiration();
		this.secure = secure;
		this.sameSite = sameSite;
		this.domain = domain;
	}

	public void write(HttpServletResponse response, String refreshToken) {
		response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, maxAge).toString());
	}

	/** 로그아웃·탈퇴 시 브라우저의 쿠키를 지운다. */
	public void clear(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
	}

	private ResponseCookie build(String value, Duration age) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(secure)
			.sameSite(sameSite)
			.path(PATH)
			.maxAge(age);
		if (!domain.isBlank()) {
			builder.domain(domain);
		}
		return builder.build();
	}
}
