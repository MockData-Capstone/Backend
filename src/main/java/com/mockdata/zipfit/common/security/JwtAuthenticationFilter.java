package com.mockdata.zipfit.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {accessToken} 헤더를 검증해 SecurityContext에 회원 ID(Long)를 principal로 넣는다.
 * 토큰이 없거나 유효하지 않으면 인증 없이 넘기고, 보호된 경로라면 {@link JwtAuthenticationEntryPoint}가 401을 응답한다.
 * 서블릿 필터로 중복 등록되지 않도록 빈으로 만들지 않고 SecurityConfig에서 직접 생성한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String TOKEN_EXPIRED_ATTRIBUTE = "zipfit.auth.tokenExpired";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		String token = resolveToken(request);
		if (token != null) {
			switch (jwtProvider.parseAccessToken(token)) {
				case AccessTokenResult.Valid valid -> {
					var authentication = UsernamePasswordAuthenticationToken.authenticated(valid.userId(), null, List.of());
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
				case AccessTokenResult.Expired expired -> request.setAttribute(TOKEN_EXPIRED_ATTRIBUTE, true);
				case AccessTokenResult.Invalid invalid -> {
				}
			}
		}
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length());
		return token.isBlank() ? null : token;
	}
}
