package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.repository.RefreshTokenRepository;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class LocalAuthIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	UserRepository userRepository;
	@Autowired
	SocialAccountRepository socialAccountRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
		socialAccountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("자체 가입하면 토큰이 발급되고 비밀번호는 해시로 저장된다")
	void signup() throws Exception {
		String body = signup("Test@Zipfit.com", "password1", "집구하는중", true)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").exists())
			.andExpect(jsonPath("$.refreshToken").exists())
			.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(body, "$.accessToken")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("test@zipfit.com"))
			.andExpect(jsonPath("$.providers[0]").value("LOCAL"));

		String saved = userRepository.findAll().getFirst().getPassword();
		assertThat(saved).isNotEqualTo("password1").startsWith("$2");
	}

	@Test
	@DisplayName("이미 가입된 이메일·사용 중인 닉네임·동의 누락·약한 비밀번호는 가입할 수 없다")
	void signupRules() throws Exception {
		signup("a@zipfit.com", "password1", "닉네임하나", true).andExpect(status().isCreated());

		signup("A@ZIPFIT.com", "password1", "다른닉네임", true)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_EMAIL.name()));
		signup("b@zipfit.com", "password1", "닉네임하나", true)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_NICKNAME.name()));
		signup("c@zipfit.com", "password1", "닉네임셋", false)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.PRIVACY_AGREEMENT_REQUIRED.name()));
		signup("d@zipfit.com", "onlyletters", "닉네임넷", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.name()));
		signup("not-an-email", "password1", "닉네임다섯", true)
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("자체 로그인: 이메일 대소문자는 무시하고, 틀리면 어느 쪽이 틀렸는지 밝히지 않는다")
	void login() throws Exception {
		signup("user@zipfit.com", "password1", "로그인테스트", true).andExpect(status().isCreated());

		login("USER@zipfit.com", "password1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").exists());

		String wrongPassword = login("user@zipfit.com", "password2")
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = login("nobody@zipfit.com", "password1")
			.andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		assertThat(wrongPassword).isEqualTo(unknownEmail).contains(ErrorCode.INVALID_CREDENTIALS.name());
	}

	@Test
	@DisplayName("이메일 중복 확인")
	void emailAvailability() throws Exception {
		signup("taken@zipfit.com", "password1", "중복확인", true).andExpect(status().isCreated());

		mockMvc.perform(get("/api/auth/email-availability").param("email", "TAKEN@zipfit.com"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false));
		mockMvc.perform(get("/api/auth/email-availability").param("email", "free@zipfit.com"))
			.andExpect(jsonPath("$.available").value(true));
		mockMvc.perform(get("/api/auth/email-availability").param("email", "not-an-email"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/auth/email-availability"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("email"));
	}

	@Test
	@DisplayName("닉네임 중복 확인: 이미 쓰는 닉네임이나 길이가 맞지 않는 닉네임은 쓸 수 없다")
	void nicknameAvailability() throws Exception {
		signup("nick@zipfit.com", "password1", "사용중닉", true).andExpect(status().isCreated());

		mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", " 사용중닉 "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false));
		mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", "새닉네임"))
			.andExpect(jsonPath("$.available").value(true));
		mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", "a".repeat(21)))
			.andExpect(jsonPath("$.available").value(false));
		mockMvc.perform(get("/api/auth/nickname-availability").param("nickname", " "))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("입력 검증 실패는 잘못된 필드를 모두 알려준다")
	void validationErrorsListEveryField() throws Exception {
		signup("not-an-email", "short", "", true)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.name()))
			.andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'nickname')]").exists());
		// 검증 실패가 아닌 에러에는 errors가 없다.
		signup("ok@zipfit.com", "password1", "동의안함", false)
			.andExpect(jsonPath("$.errors").doesNotExist());
	}

	private ResultActions signup(String email, String password, String nickname, boolean privacyAgreed) throws Exception {
		String body = "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\",\"privacyAgreed\":%s}"
			.formatted(email, password, nickname, privacyAgreed);
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions login(String email, String password) throws Exception {
		String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
