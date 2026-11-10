package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.controller.RefreshTokenCookie;
import com.mockdata.zipfit.auth.entity.RefreshToken;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.SecureTokens;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RefreshCookieIntegrationTest extends AuthTestSupport {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "unknown-refresh-token")
	@DisplayName("쿠키가 없거나 비어 있거나 잘못되면 401이며 다른 유효한 세션에는 영향이 없다")
	void invalidCookieDoesNotRevokeValidSession(String refreshToken) throws Exception {
		Tokens active = signupLocal("cookie@zipfit.com", "password1", "쿠키검증");
		MockHttpServletRequestBuilder request = post("/api/auth/token/refresh");
		if (refreshToken != null) {
			request.cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken));
		}

		expectInvalidRefresh(mockMvc.perform(request));
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		reissue(active.refreshToken()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("유효한 재발급 토큰도 JSON 본문으로만 보내면 거절하고 쿠키로 보내야 성공한다")
	void bodyCannotReplaceRefreshCookie() throws Exception {
		Tokens active = signupLocal("body@zipfit.com", "password1", "본문검증");

		expectInvalidRefresh(mockMvc.perform(json(post("/api/auth/token/refresh"),
			"{\"refreshToken\":\"%s\"}".formatted(active.refreshToken()))));
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		reissue(active.refreshToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().httpOnly(RefreshTokenCookie.NAME, true))
			.andExpect(cookie().path(RefreshTokenCookie.NAME, "/api/auth"));
	}

	@Test
	@DisplayName("DB에 남아 있는 만료 쿠키도 거절하고 같은 회원의 유효한 세션은 유지한다")
	void expiredCookieCannotIssueAccessToken() throws Exception {
		Tokens active = signupLocal("expired@zipfit.com", "password1", "쿠키만료");
		String expiredToken = "expired-refresh-cookie";
		refreshTokenRepository.saveAndFlush(new RefreshToken(userRepository.findAll().getFirst(),
			SecureTokens.sha256(expiredToken), Instant.now().minusSeconds(60)));

		expectInvalidRefresh(reissue(expiredToken));
		reissue(active.refreshToken()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("접근 토큰 없이 로그아웃해도 해당 기기의 쿠키만 폐기하며 반복 요청도 성공한다")
	void logoutRevokesOnlyCurrentDevice() throws Exception {
		Tokens first = signupLocal("devices@zipfit.com", "password1", "다중기기");
		Tokens second = tokens(login("devices@zipfit.com", "password1")
			.andExpect(status().isOk()).andReturn());
		assertThat(refreshTokenRepository.count()).isEqualTo(2);

		expectLogoutCookieCleared(first.refreshToken());
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		expectInvalidRefresh(reissue(first.refreshToken()));
		expectLogoutCookieCleared(first.refreshToken());
		reissue(second.refreshToken()).andExpect(status().isOk());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "unknown-refresh-token")
	@DisplayName("로그아웃 쿠키가 없거나 무효여도 브라우저 쿠키를 지우고 다른 세션은 유지한다")
	void logoutWithInvalidCookieIsIdempotent(String refreshToken) throws Exception {
		Tokens active = signupLocal("logout@zipfit.com", "password1", "로그아웃검증");

		expectLogoutCookieCleared(refreshToken);
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		reissue(active.refreshToken()).andExpect(status().isOk());
	}

	@Test
	@DisplayName("같은 쿠키의 동시 재발급은 한 번만 성공하고 실패 응답은 새 쿠키를 덮어쓰지 않는다")
	void concurrentRefreshHasSingleWinner() throws Exception {
		Tokens active = signupLocal("concurrent@zipfit.com", "password1", "동시재발급");
		var executor = Executors.newFixedThreadPool(2);
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try {
			Callable<MvcResult> refresh = () -> {
				ready.countDown();
				assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
				return reissue(active.refreshToken()).andReturn();
			};
			var first = executor.submit(refresh);
			var second = executor.submit(refresh);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<MvcResult> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

			assertThat(results).extracting(result -> result.getResponse().getStatus())
				.containsExactlyInAnyOrder(200, 401);
			MvcResult winner = results.stream().filter(result -> result.getResponse().getStatus() == 200)
				.findFirst().orElseThrow();
			MvcResult loser = results.stream().filter(result -> result.getResponse().getStatus() == 401)
				.findFirst().orElseThrow();
			assertThat(JsonPath.<String>read(loser.getResponse().getContentAsString(), "$.code"))
				.isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.name());
			assertThat(loser.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
			assertThat(refreshTokenRepository.count()).isEqualTo(1);
			Tokens rotated = tokens(winner);
			assertThat(rotated.refreshToken()).isNotEqualTo(active.refreshToken());
			mockMvc.perform(bearer(get("/api/users/me"), rotated.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("concurrent@zipfit.com"));
			reissue(rotated.refreshToken()).andExpect(status().isOk());
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private void expectInvalidRefresh(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_TOKEN.name()))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	private void expectLogoutCookieCleared(String refreshToken) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/auth/logout");
		if (refreshToken != null) {
			request.cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken));
		}
		mockMvc.perform(request)
			.andExpect(status().isNoContent())
			.andExpect(cookie().value(RefreshTokenCookie.NAME, ""))
			.andExpect(cookie().maxAge(RefreshTokenCookie.NAME, 0))
			.andExpect(cookie().path(RefreshTokenCookie.NAME, "/api/auth"))
			.andExpect(cookie().httpOnly(RefreshTokenCookie.NAME, true))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
	}
}
