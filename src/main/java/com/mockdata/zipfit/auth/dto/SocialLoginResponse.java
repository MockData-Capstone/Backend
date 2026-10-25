package com.mockdata.zipfit.auth.dto;

/**
 * 소셜 로그인 결과.
 * <ul>
 *   <li>LOGIN: 가입된 회원이라 tokens를 바로 발급했다.</li>
 *   <li>SIGNUP_REQUIRED: 처음 온 회원이다. signup의 signupToken과 함께 동의·닉네임을 받아 /api/auth/signup/social 로 가입을 마친다.</li>
 * </ul>
 */
public record SocialLoginResponse(Status status, TokenResponse tokens, SignupRequired signup) {

	public enum Status {
		LOGIN, SIGNUP_REQUIRED
	}

	/**
	 * @param nickname          제공자 닉네임(가입 화면 기본값). 없을 수 있다.
	 * @param nicknameAvailable false면 가입 화면에서 새 닉네임을 입력받는다.
	 */
	public record SignupRequired(
		String signupToken,
		String nickname,
		boolean nicknameAvailable,
		String email,
		String profileImageUrl
	) {
	}

	public static SocialLoginResponse login(TokenResponse tokens) {
		return new SocialLoginResponse(Status.LOGIN, tokens, null);
	}

	public static SocialLoginResponse signupRequired(SignupRequired signup) {
		return new SocialLoginResponse(Status.SIGNUP_REQUIRED, null, signup);
	}
}
