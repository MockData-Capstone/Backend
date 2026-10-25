package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.oauth.GoogleOAuthClient;
import com.mockdata.zipfit.auth.oauth.KakaoOAuthClient;
import com.mockdata.zipfit.auth.oauth.OAuthUserInfo;
import com.mockdata.zipfit.auth.repository.RefreshTokenRepository;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	UserRepository userRepository;
	@Autowired
	SocialAccountRepository socialAccountRepository;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	KakaoOAuthClient kakaoClient;
	@MockitoBean
	GoogleOAuthClient googleClient;

	@BeforeEach
	void setUp() {
		given(kakaoClient.provider()).willReturn(OAuthProvider.KAKAO);
		given(googleClient.provider()).willReturn(OAuthProvider.GOOGLE);
	}

	@AfterEach
	void tearDown() {
		refreshTokenRepository.deleteAll();
		socialAccountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("처음 온 카카오 회원은 가입 토큰을 받고 동의 후 가입하면 토큰이 발급된다")
	void kakaoSignupFlow() throws Exception {
		givenKakaoUser("kakao-code", "1001", "집찾는사람", "a@kakao.com", null);

		String login = socialLogin("kakao", "kakao-code");
		assertThat(JsonPath.<String>read(login, "$.status")).isEqualTo("SIGNUP_REQUIRED");
		assertThat(JsonPath.<Boolean>read(login, "$.signup.nicknameAvailable")).isTrue();

		Tokens tokens = signup(JsonPath.read(login, "$.signup.signupToken"), "집찾는사람");

		mockMvc.perform(bearer(get("/api/users/me"), tokens.accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("집찾는사람"))
			.andExpect(jsonPath("$.email").value("a@kakao.com"))
			.andExpect(jsonPath("$.providers[0]").value("KAKAO"));
	}

	@Test
	@DisplayName("이미 가입한 회원은 소셜 로그인 시 바로 토큰을 받는다")
	void existingUserLogin() throws Exception {
		given(googleClient.fetchUserInfo("g-code"))
			.willReturn(new OAuthUserInfo(OAuthProvider.GOOGLE, "google-sub-1", "g@gmail.com", "구글러", null));
		signup(JsonPath.read(socialLogin("google", "g-code"), "$.signup.signupToken"), "구글러");

		mockMvc.perform(json(post("/api/auth/google"), "{\"code\":\"g-code\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("LOGIN"))
			.andExpect(jsonPath("$.tokens.accessToken").exists())
			.andExpect(jsonPath("$.tokens.refreshToken").exists());
	}

	@Test
	@DisplayName("개인정보 동의 없이는 가입할 수 없다")
	void privacyAgreementRequired() throws Exception {
		givenKakaoUser("c", "1", "닉네임", null, null);
		String signupToken = JsonPath.read(socialLogin("kakao", "c"), "$.signup.signupToken");

		mockMvc.perform(json(post("/api/auth/signup/social"), signupBody(signupToken, "닉네임", false)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.PRIVACY_AGREEMENT_REQUIRED.name()));
	}

	@Test
	@DisplayName("카카오 닉네임이 이미 사용 중이면 새 닉네임을 받아야 한다")
	void duplicateNickname() throws Exception {
		givenKakaoUser("c1", "1", "중복닉", null, null);
		signup(JsonPath.read(socialLogin("kakao", "c1"), "$.signup.signupToken"), "중복닉");

		givenKakaoUser("c2", "2", "중복닉", null, null);
		String login = socialLogin("kakao", "c2");
		assertThat(JsonPath.<Boolean>read(login, "$.signup.nicknameAvailable")).isFalse();
		String signupToken = JsonPath.read(login, "$.signup.signupToken");

		mockMvc.perform(json(post("/api/auth/signup/social"), signupBody(signupToken, "중복닉", true)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_NICKNAME.name()));
		signup(signupToken, "새닉네임");
	}

	@Test
	@DisplayName("소셜 인증에 실패하면 401을 응답한다")
	void oauthFailure() throws Exception {
		given(kakaoClient.fetchUserInfo("bad")).willThrow(new BusinessException(ErrorCode.OAUTH_FAILED));

		mockMvc.perform(json(post("/api/auth/kakao"), "{\"code\":\"bad\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.OAUTH_FAILED.name()));
	}

	@Test
	@DisplayName("재발급하면 재발급 토큰도 바뀌고 이전 토큰은 쓸 수 없다")
	void reissueRotatesRefreshToken() throws Exception {
		Tokens tokens = registerKakaoUser("500", null);

		String reissued = mockMvc.perform(json(post("/api/auth/reissue"), refreshBody(tokens.refreshToken())))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String newRefreshToken = JsonPath.read(reissued, "$.refreshToken");
		assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());

		mockMvc.perform(json(post("/api/auth/reissue"), refreshBody(tokens.refreshToken())))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REFRESH_TOKEN.name()));
		mockMvc.perform(json(post("/api/auth/reissue"), refreshBody(newRefreshToken)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("로그아웃하면 재발급 토큰이 폐기된다")
	void logoutRevokesRefreshToken() throws Exception {
		Tokens tokens = registerKakaoUser("500", null);

		mockMvc.perform(json(post("/api/auth/logout"), refreshBody(tokens.refreshToken())))
			.andExpect(status().isNoContent());
		mockMvc.perform(json(post("/api/auth/reissue"), refreshBody(tokens.refreshToken())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("토큰 없이 또는 가입 토큰으로는 내 정보를 조회할 수 없다")
	void meRequiresAccessToken() throws Exception {
		mockMvc.perform(get("/api/users/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

		givenKakaoUser("c", "1", "닉네임", null, null);
		String signupToken = JsonPath.read(socialLogin("kakao", "c"), "$.signup.signupToken");
		mockMvc.perform(bearer(get("/api/users/me"), signupToken))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("닉네임을 수정하고 프로필 이미지를 기본으로 되돌린다")
	void updateNicknameAndResetProfileImage() throws Exception {
		Tokens tokens = registerKakaoUser("500", "https://img.kakao.com/p.jpg");

		mockMvc.perform(json(bearer(patch("/api/users/me"), tokens.accessToken()), "{\"nickname\":\"바뀐닉네임\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("바뀐닉네임"))
			.andExpect(jsonPath("$.profileImageUrl").value("https://img.kakao.com/p.jpg"));
		mockMvc.perform(bearer(delete("/api/users/me/profile-image"), tokens.accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.profileImageUrl").doesNotExist());
	}

	@Test
	@DisplayName("탈퇴하면 카카오 연결을 끊고 회원 정보와 토큰을 삭제한다")
	void withdraw() throws Exception {
		Tokens tokens = registerKakaoUser("777", null);

		mockMvc.perform(bearer(delete("/api/users/me"), tokens.accessToken()))
			.andExpect(status().isNoContent());

		verify(kakaoClient).unlink("777");
		assertThat(userRepository.count()).isZero();
		assertThat(refreshTokenRepository.count()).isZero();
		mockMvc.perform(bearer(get("/api/users/me"), tokens.accessToken()))
			.andExpect(status().isNotFound());
		mockMvc.perform(json(post("/api/auth/kakao"), "{\"code\":\"code-777\"}"))
			.andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"));
	}

	private record Tokens(String accessToken, String refreshToken) {
	}

	private Tokens registerKakaoUser(String kakaoId, String profileImageUrl) throws Exception {
		givenKakaoUser("code-" + kakaoId, kakaoId, "회원" + kakaoId, null, profileImageUrl);
		String signupToken = JsonPath.read(socialLogin("kakao", "code-" + kakaoId), "$.signup.signupToken");
		return signup(signupToken, "회원" + kakaoId);
	}

	private void givenKakaoUser(String code, String id, String nickname, String email, String profileImageUrl) {
		given(kakaoClient.fetchUserInfo(code))
			.willReturn(new OAuthUserInfo(OAuthProvider.KAKAO, id, email, nickname, profileImageUrl));
	}

	private String socialLogin(String provider, String code) throws Exception {
		return mockMvc.perform(json(post("/api/auth/" + provider), "{\"code\":\"" + code + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}

	private Tokens signup(String signupToken, String nickname) throws Exception {
		String body = mockMvc.perform(json(post("/api/auth/signup/social"), signupBody(signupToken, nickname, true)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private static String signupBody(String signupToken, String nickname, boolean privacyAgreed) {
		return "{\"signupToken\":\"%s\",\"nickname\":\"%s\",\"privacyAgreed\":%s}".formatted(signupToken, nickname, privacyAgreed);
	}

	private static String refreshBody(String refreshToken) {
		return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
		return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}
}
