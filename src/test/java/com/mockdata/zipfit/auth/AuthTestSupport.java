package com.mockdata.zipfit.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.controller.RefreshTokenCookie;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.oauth.GoogleOAuthClient;
import com.mockdata.zipfit.auth.oauth.KakaoOAuthClient;
import com.mockdata.zipfit.auth.oauth.OAuthUserInfo;
import com.mockdata.zipfit.auth.repository.PasswordResetTokenRepository;
import com.mockdata.zipfit.auth.repository.RefreshTokenRepository;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.mail.MailService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** 통합 테스트 공통 설정. 외부 연동(카카오·구글·메일)은 mock으로 대신한다. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AuthTestSupport {

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected UserRepository userRepository;
	@Autowired
	protected SocialAccountRepository socialAccountRepository;
	@Autowired
	protected RefreshTokenRepository refreshTokenRepository;
	@Autowired
	protected PasswordResetTokenRepository passwordResetTokenRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	protected KakaoOAuthClient kakaoClient;
	@MockitoBean
	protected GoogleOAuthClient googleClient;
	@MockitoBean
	protected MailService mailService;

	protected record Tokens(String accessToken, String refreshToken) {
	}

	@BeforeEach
	void setUpOAuthMocks() {
		given(kakaoClient.provider()).willReturn(OAuthProvider.KAKAO);
		given(googleClient.provider()).willReturn(OAuthProvider.GOOGLE);
	}

	/**
	 * 도메인이 늘어도 고치지 않도록 모든 테이블을 비운다.
	 * ID는 이어서 쓴다. 앞 테스트의 WebSocket 연결이 남아 있어도 새 회원·Workspace와 ID가 겹치지 않게 하기 위해서다.
	 */
	@AfterEach
	void cleanUp() {
		List<String> tables = jdbcTemplate.queryForList(
			"select table_name from information_schema.tables where lower(table_schema) = 'public' and table_type = 'BASE TABLE'",
			String.class
		);
		jdbcTemplate.execute("set referential_integrity false");
		tables.forEach(table -> jdbcTemplate.execute("truncate table \"" + table + "\""));
		jdbcTemplate.execute("set referential_integrity true");
	}

	protected Tokens signupLocal(String email, String password, String nickname) throws Exception {
		return tokens(mockMvc.perform(json(post("/api/auth/signup"),
				"{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\",\"privacyAgreed\":true}"
					.formatted(email, password, nickname)))
			.andExpect(status().isCreated())
			.andReturn());
	}

	protected void givenKakaoUser(String code, String kakaoId, String nickname, String email) {
		given(kakaoClient.fetchUserInfo(code))
			.willReturn(new OAuthUserInfo(OAuthProvider.KAKAO, kakaoId, email, nickname, null));
	}

	/** 카카오 전용 회원을 만든다 (이메일 없음). */
	protected Tokens signupKakao(String kakaoId, String nickname) throws Exception {
		givenKakaoUser("code-" + kakaoId, kakaoId, nickname, null);
		String login = kakaoLogin("code-" + kakaoId).andReturn().getResponse().getContentAsString();
		return tokens(mockMvc.perform(json(post("/api/auth/signup/social"),
				"{\"signupToken\":\"%s\",\"nickname\":\"%s\",\"privacyAgreed\":true}"
					.formatted(JsonPath.<String>read(login, "$.signup.signupToken"), nickname)))
			.andExpect(status().isCreated())
			.andReturn());
	}

	protected ResultActions kakaoLogin(String code) throws Exception {
		return mockMvc.perform(json(post("/api/auth/kakao"), "{\"code\":\"%s\"}".formatted(code)));
	}

	protected ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(json(post("/api/auth/login"),
			"{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	/** 재발급 토큰은 쿠키로 보낸다. */
	protected ResultActions reissue(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/token/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken)));
	}

	/** 접근 토큰은 응답 본문에서, 재발급 토큰은 httpOnly 쿠키에서 꺼낸다. */
	protected static Tokens tokens(MvcResult result) throws Exception {
		Cookie cookie = result.getResponse().getCookie(RefreshTokenCookie.NAME);
		return new Tokens(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"),
			cookie == null ? null : cookie.getValue());
	}

	protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	protected static <B extends AbstractMockHttpServletRequestBuilder<B>> B bearer(B builder, String accessToken) {
		return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
	}
}
