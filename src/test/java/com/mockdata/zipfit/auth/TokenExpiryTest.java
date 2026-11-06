package com.mockdata.zipfit.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.JwtProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TokenExpiryTest extends AuthTestSupport {

	@Autowired
	JwtProvider jwtProvider;

	@Test
	@DisplayName("만료된 접근 토큰은 401 AUTH_TOKEN_EXPIRED로 구분해 프론트가 재발급하게 한다")
	void expiredAccessToken() throws Exception {
		signupLocal("exp@zipfit.com", "password1", "만료테스트");
		Long userId = userRepository.findAll().getFirst().getId();
		String expired = jwtProvider.createAccessToken(userId, Instant.now().minus(Duration.ofHours(1)));

		mockMvc.perform(bearer(get("/api/users/me"), expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.AUTH_TOKEN_EXPIRED.name()));
	}
}
