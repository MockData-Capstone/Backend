package com.mockdata.zipfit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-08 카카오 계정 연동, AUTH-02 같은 이메일 자체 가입 회원 안내 */
class AccountLinkIntegrationTest extends AuthTestSupport {

	@Test
	@DisplayName("카카오 로그인 시 같은 이메일의 자체 가입 회원이 있으면 새로 만들지 않고 409와 연동 토큰을 준다")
	void kakaoLoginWithSameEmailRequiresLink() throws Exception {
		signupLocal("same@zipfit.com", "password1", "자체회원");
		givenKakaoUser("k1", "900", "카카오닉", "Same@Zipfit.com");

		String body = kakaoLogin("k1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LINK_REQUIRED.name()))
			.andExpect(jsonPath("$.email").value("same@zipfit.com"))
			.andReturn().getResponse().getContentAsString();
		String linkToken = JsonPath.read(body, "$.linkToken");
		assertThat(userRepository.count()).isEqualTo(1);

		linkWithToken("kakao", linkToken, "wrong1234")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PASSWORD.name()));
		assertThat(socialAccountRepository.count()).isZero();

		String linked = linkWithToken("kakao", linkToken, "password1")
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		Tokens tokens = tokens(linked, "$");

		mockMvc.perform(bearer(get("/api/users/me"), tokens.accessToken()))
			.andExpect(jsonPath("$.nickname").value("자체회원"))
			.andExpect(jsonPath("$.providers[0]").value("LOCAL"))
			.andExpect(jsonPath("$.providers[1]").value("KAKAO"));
		assertThat(userRepository.count()).isEqualTo(1);

		// 이후에는 두 방식 모두로 로그인된다.
		kakaoLogin("k1").andExpect(jsonPath("$.status").value("LOGIN"));
		login("same@zipfit.com", "password1").andExpect(status().isOk());
	}

	@Test
	@DisplayName("연동 토큰은 발급한 제공자 경로에서만 쓸 수 있다")
	void linkTokenProviderMustMatch() throws Exception {
		signupLocal("p@zipfit.com", "password1", "제공자확인");
		givenKakaoUser("k2", "901", "닉", "p@zipfit.com");
		String linkToken = JsonPath.read(kakaoLogin("k2").andReturn().getResponse().getContentAsString(), "$.linkToken");

		linkWithToken("google", linkToken, "password1")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_LINK_TOKEN.name()));
	}

	@Test
	@DisplayName("가입 토큰을 받은 뒤 같은 이메일로 자체 가입했다면 소셜 가입 대신 연동해야 한다")
	void socialSignupBlockedWhenLocalAccountAppears() throws Exception {
		givenKakaoUser("k3", "902", "늦은카카오", "late@zipfit.com");
		String signupToken = JsonPath.read(kakaoLogin("k3").andReturn().getResponse().getContentAsString(), "$.signup.signupToken");
		signupLocal("late@zipfit.com", "password1", "먼저가입");

		mockMvc.perform(json(post("/api/auth/signup/social"),
				"{\"signupToken\":\"%s\",\"nickname\":\"늦은카카오\",\"privacyAgreed\":true}".formatted(signupToken)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_LINK_REQUIRED.name()));
	}

	@Test
	@DisplayName("내 정보 화면에서 비밀번호를 확인하고 카카오 계정을 연동한다")
	void linkFromMyPage() throws Exception {
		Tokens tokens = signupLocal("my@zipfit.com", "password1", "마이페이지");
		givenKakaoUser("k4", "903", "닉", null);

		linkFromMyPage(tokens.accessToken(), "k4", "password1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.providers[0]").value("LOCAL"))
			.andExpect(jsonPath("$.providers[1]").value("KAKAO"));

		linkFromMyPage(tokens.accessToken(), "k4", "password1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.PROVIDER_ALREADY_LINKED.name()));
		kakaoLogin("k4").andExpect(jsonPath("$.status").value("LOGIN"));
	}

	@Test
	@DisplayName("이미 다른 회원에 연동된 카카오 계정은 연동할 수 없다")
	void kakaoAccountLinkedToOtherUser() throws Exception {
		signupKakao("904", "먼저카카오");
		Tokens tokens = signupLocal("other@zipfit.com", "password1", "다른회원");
		givenKakaoUser("k5", "904", "먼저카카오", null);

		linkFromMyPage(tokens.accessToken(), "k5", "password1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED.name()));
	}

	@Test
	@DisplayName("비밀번호가 틀리거나 비밀번호 없는 회원이면 카카오 API를 부르기 전에 막는다")
	void myPageLinkRejectedBeforeCallingKakao() throws Exception {
		Tokens local = signupLocal("x@zipfit.com", "password1", "비번틀림");
		Tokens kakaoOnly = signupKakao("905", "카카오만");

		linkFromMyPage(local.accessToken(), "never-used", "wrong1234")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PASSWORD.name()));
		linkFromMyPage(kakaoOnly.accessToken(), "never-used", "anything1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_NOT_SET.name()));
		verify(kakaoClient, never()).fetchUserInfo("never-used");
		verify(googleClient, never()).fetchUserInfo(anyString());
	}

	private ResultActions linkWithToken(String provider, String linkToken, String password) throws Exception {
		return mockMvc.perform(json(post("/api/auth/" + provider + "/link"),
			"{\"linkToken\":\"%s\",\"password\":\"%s\"}".formatted(linkToken, password)));
	}

	private ResultActions linkFromMyPage(String accessToken, String code, String password) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/users/me/social/kakao"), accessToken),
			"{\"code\":\"%s\",\"password\":\"%s\"}".formatted(code, password)));
	}
}
