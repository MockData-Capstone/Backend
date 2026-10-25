package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.exception.ErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-05 비밀번호 변경, AUTH-07 비밀번호 재설정 */
class PasswordIntegrationTest extends AuthTestSupport {

	private static final Pattern RESET_TOKEN = Pattern.compile("password-reset\\?token=([A-Za-z0-9_-]+)");

	@Test
	@DisplayName("AUTH-05 현재 비밀번호가 맞으면 비밀번호를 바꾸고, 새 비밀번호로 로그인된다")
	void changePassword() throws Exception {
		Tokens tokens = signupLocal("pw@zipfit.com", "password1", "비번변경");

		changePassword(tokens.accessToken(), "wrong1234", "newpass99")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PASSWORD.name()));
		changePassword(tokens.accessToken(), "password1", "short1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.name()));

		changePassword(tokens.accessToken(), "password1", "newpass99").andExpect(status().isNoContent());

		login("pw@zipfit.com", "password1").andExpect(status().isUnauthorized());
		login("pw@zipfit.com", "newpass99").andExpect(status().isOk());
	}

	@Test
	@DisplayName("AUTH-05 비밀번호가 없는 카카오 전용 회원은 비밀번호를 바꿀 수 없다")
	void kakaoOnlyCannotChangePassword() throws Exception {
		Tokens tokens = signupKakao("100", "카카오회원");

		changePassword(tokens.accessToken(), "anything1", "newpass99")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_NOT_SET.name()));
	}

	@Test
	@DisplayName("AUTH-07 재설정 링크로 비밀번호를 바꾸면 다른 기기 로그인이 끊기고, 링크는 한 번만 쓸 수 있다")
	void resetPassword() throws Exception {
		Tokens tokens = signupLocal("reset@zipfit.com", "password1", "재설정");

		requestReset("RESET@zipfit.com").andExpect(status().isNoContent());
		String token = captureResetToken("reset@zipfit.com");

		confirmReset(token, "resetpass1").andExpect(status().isNoContent());

		reissue(tokens.refreshToken()).andExpect(status().isUnauthorized());
		login("reset@zipfit.com", "password1").andExpect(status().isUnauthorized());
		login("reset@zipfit.com", "resetpass1").andExpect(status().isOk());

		confirmReset(token, "another12")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_RESET_TOKEN.name()));
	}

	@Test
	@DisplayName("AUTH-07 다시 요청하면 이전 링크는 무효가 된다")
	void newRequestInvalidatesPreviousLink() throws Exception {
		signupLocal("again@zipfit.com", "password1", "재요청");

		requestReset("again@zipfit.com");
		String first = captureResetToken("again@zipfit.com");
		clearInvocations(mailService);
		requestReset("again@zipfit.com");
		String second = captureResetToken("again@zipfit.com");

		confirmReset(first, "resetpass1").andExpect(status().isBadRequest());
		confirmReset(second, "resetpass1").andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("AUTH-07 가입되지 않은 이메일도 같은 응답을 주고, 메일은 보내지 않는다")
	void unknownEmail() throws Exception {
		requestReset("nobody@zipfit.com").andExpect(status().isNoContent());

		verify(mailService, never()).send(anyString(), anyString(), anyString());
		assertThat(passwordResetTokenRepository.count()).isZero();
	}

	@Test
	@DisplayName("AUTH-07 카카오 전용 회원에게는 재설정 링크 대신 카카오 로그인 안내 메일을 보낸다")
	void kakaoOnlyGetsKakaoGuide() throws Exception {
		givenKakaoUser("k-code", "200", "카카오이메일", "kakao@zipfit.com");
		String login = kakaoLogin("k-code").andReturn().getResponse().getContentAsString();
		String signupToken = com.jayway.jsonpath.JsonPath.read(login, "$.signup.signupToken");
		mockMvc.perform(json(post("/api/auth/signup/social"),
			"{\"signupToken\":\"%s\",\"nickname\":\"카카오이메일\",\"privacyAgreed\":true}".formatted(signupToken)))
			.andExpect(status().isCreated());

		requestReset("kakao@zipfit.com").andExpect(status().isNoContent());

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailService).send(eq("kakao@zipfit.com"), anyString(), text.capture());
		assertThat(text.getValue()).contains("카카오").doesNotContain("token=");
		assertThat(passwordResetTokenRepository.count()).isZero();
	}

	@Test
	@DisplayName("AUTH-06 재설정을 요청한 회원도 탈퇴하면 재설정 토큰까지 삭제된다")
	void withdrawDeletesResetTokens() throws Exception {
		Tokens tokens = signupLocal("bye@zipfit.com", "password1", "탈퇴예정");
		requestReset("bye@zipfit.com");
		assertThat(passwordResetTokenRepository.count()).isEqualTo(1);

		mockMvc.perform(bearer(delete("/api/users/me"), tokens.accessToken())).andExpect(status().isNoContent());

		assertThat(passwordResetTokenRepository.count()).isZero();
		assertThat(userRepository.count()).isZero();
	}

	private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
		return mockMvc.perform(json(bearer(patch("/api/users/me/password"), accessToken),
			"{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
	}

	private ResultActions requestReset(String email) throws Exception {
		return mockMvc.perform(json(post("/api/auth/password-reset/request"), "{\"email\":\"%s\"}".formatted(email)));
	}

	private ResultActions confirmReset(String token, String newPassword) throws Exception {
		return mockMvc.perform(json(post("/api/auth/password-reset/confirm"),
			"{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
	}

	private String captureResetToken(String email) {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailService).send(eq(email), anyString(), text.capture());
		Matcher matcher = RESET_TOKEN.matcher(text.getValue());
		assertThat(matcher.find()).as("메일에 재설정 링크가 있어야 한다").isTrue();
		return matcher.group(1);
	}
}
