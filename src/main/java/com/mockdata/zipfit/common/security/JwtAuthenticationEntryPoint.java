package com.mockdata.zipfit.common.security;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 유효한 접근 토큰 없이 보호된 경로를 요청하면 401을 JSON으로 응답한다. 만료는 따로 구분해 프론트가 재발급(AUTH-09)하게 한다. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
		throws IOException {
		ErrorCode errorCode = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.TOKEN_EXPIRED_ATTRIBUTE))
			? ErrorCode.AUTH_TOKEN_EXPIRED
			: ErrorCode.UNAUTHORIZED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(errorCode)));
	}
}
