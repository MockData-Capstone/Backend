package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.auth.dto.AuthorizeUrlResponse;
import com.mockdata.zipfit.auth.dto.RefreshTokenRequest;
import com.mockdata.zipfit.auth.dto.SocialLoginRequest;
import com.mockdata.zipfit.auth.dto.SocialLoginResponse;
import com.mockdata.zipfit.auth.dto.SocialSignupRequest;
import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.service.AuthService;
import com.mockdata.zipfit.auth.service.TokenService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final TokenService tokenService;

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

	/** AUTH-09 토큰 재발급 */
	@PostMapping("/reissue")
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
