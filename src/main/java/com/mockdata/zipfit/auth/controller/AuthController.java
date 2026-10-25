package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.auth.dto.AccountLinkRequest;
import com.mockdata.zipfit.auth.dto.AuthorizeUrlResponse;
import com.mockdata.zipfit.auth.dto.AvailabilityResponse;
import com.mockdata.zipfit.auth.dto.LoginRequest;
import com.mockdata.zipfit.auth.dto.PasswordResetConfirmRequest;
import com.mockdata.zipfit.auth.dto.PasswordResetRequest;
import com.mockdata.zipfit.auth.dto.RefreshTokenRequest;
import com.mockdata.zipfit.auth.dto.SignupRequest;
import com.mockdata.zipfit.auth.dto.SocialLoginRequest;
import com.mockdata.zipfit.auth.dto.SocialLoginResponse;
import com.mockdata.zipfit.auth.dto.SocialSignupRequest;
import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.service.AccountLinkService;
import com.mockdata.zipfit.auth.service.AuthService;
import com.mockdata.zipfit.auth.service.LocalAuthService;
import com.mockdata.zipfit.auth.service.PasswordService;
import com.mockdata.zipfit.auth.service.TokenService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final LocalAuthService localAuthService;
	private final AccountLinkService accountLinkService;
	private final PasswordService passwordService;
	private final TokenService tokenService;

	/** AUTH-01 자체 회원가입. 가입하면 바로 로그인되도록 토큰을 발급한다. */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse signup(@Valid @RequestBody SignupRequest request) {
		return localAuthService.signup(request);
	}

	/** AUTH-01 이메일 중복 확인 */
	@GetMapping("/email-availability")
	public AvailabilityResponse emailAvailability(@RequestParam @NotBlank @Email String email) {
		return new AvailabilityResponse(localAuthService.isEmailAvailable(email));
	}

	/** AUTH-01·02 닉네임 중복 확인. 가입 화면에서 닉네임을 정하기 전에 쓴다. */
	@GetMapping("/nickname-availability")
	public AvailabilityResponse nicknameAvailability(@RequestParam @NotBlank String nickname) {
		return new AvailabilityResponse(localAuthService.isNicknameAvailable(nickname));
	}

	/** AUTH-03 자체 로그인 */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return localAuthService.login(request);
	}

	/** AUTH-02 소셜 로그인 시작. 프론트는 받은 URL로 이동한다. provider = kakao | google */
	@GetMapping("/{provider:kakao|google}/authorize-url")
	public AuthorizeUrlResponse authorizeUrl(@PathVariable String provider) {
		return new AuthorizeUrlResponse(authService.authorizeUrl(toProvider(provider)));
	}

	/** AUTH-02 소셜 로그인. provider = kakao | google */
	@PostMapping("/{provider:kakao|google}")
	public SocialLoginResponse socialLogin(@PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
		return authService.socialLogin(toProvider(provider), request.code());
	}

	/** AUTH-02 소셜 최초 가입 (개인정보 동의 + 닉네임 확정) */
	@PostMapping("/signup/social")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse socialSignup(@Valid @RequestBody SocialSignupRequest request) {
		return authService.socialSignup(request);
	}

	/** AUTH-08 소셜 로그인 중 연동: 409 ACCOUNT_LINK_REQUIRED 응답의 linkToken + 기존 계정 비밀번호 → 연동 후 로그인 */
	@PostMapping("/{provider:kakao|google}/link")
	public TokenResponse linkAccount(@PathVariable String provider, @Valid @RequestBody AccountLinkRequest request) {
		return accountLinkService.linkWithToken(toProvider(provider), request.linkToken(), request.password());
	}

	/** AUTH-07 비밀번호 재설정 메일 요청. 가입 여부와 상관없이 항상 204 */
	@PostMapping("/password-reset/request")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		passwordService.requestReset(request.email());
	}

	/** AUTH-07 메일 링크의 토큰으로 새 비밀번호 설정. 모든 기기에서 로그아웃된다. */
	@PostMapping("/password-reset/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		passwordService.confirmReset(request.token(), request.newPassword());
	}

	/** AUTH-09 토큰 재발급. 재발급 토큰도 새로 바꿔 준다(rotation). */
	@PostMapping("/token/refresh")
	public TokenResponse reissue(@Valid @RequestBody RefreshTokenRequest request) {
		return tokenService.reissue(request.refreshToken());
	}

	/** AUTH-09 로그아웃 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshTokenRequest request) {
		tokenService.revoke(request.refreshToken());
	}

	private OAuthProvider toProvider(String path) {
		return OAuthProvider.fromPath(path).orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
	}
}
