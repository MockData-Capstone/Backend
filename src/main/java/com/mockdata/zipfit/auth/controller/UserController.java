package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.auth.dto.AvailabilityResponse;
import com.mockdata.zipfit.auth.dto.SocialLinkRequest;
import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.service.AccountLinkService;
import com.mockdata.zipfit.auth.service.UserService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final AccountLinkService accountLinkService;

	/** AUTH-04 내 정보 조회 */
	@GetMapping("/me")
	public UserResponse getMe(@CurrentUser Long userId) {
		return userService.getMe(userId);
	}

	/** AUTH-05 내 정보 수정. 닉네임·비밀번호 중 전달된 필드만 바꾼다. */
	@PatchMapping("/me")
	public UserResponse updateMe(@CurrentUser Long userId, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateMe(userId, request);
	}

	/** AUTH-05 프로필 이미지 변경 (multipart, 필드명 image). image를 비우거나 빼면 기본 이미지로 되돌린다. */
	@PatchMapping("/me/profile-image")
	public UserResponse updateProfileImage(
		@CurrentUser Long userId,
		@RequestPart(value = "image", required = false) MultipartFile image
	) {
		return userService.updateProfileImage(userId, image);
	}

	/** AUTH-01·05 닉네임 중복 확인. 가입 화면에서도 쓰므로 로그인하지 않아도 된다. */
	@GetMapping("/nickname-availability")
	public AvailabilityResponse nicknameAvailability(@RequestParam @NotBlank String nickname) {
		return new AvailabilityResponse(userService.isNicknameAvailable(nickname));
	}

	/** AUTH-08 내 정보 화면에서 소셜 계정 연동 (자체 가입 회원만) */
	@PostMapping("/me/social/{provider:kakao|google}")
	public UserResponse linkSocialAccount(
		@CurrentUser Long userId,
		@PathVariable String provider,
		@Valid @RequestBody SocialLinkRequest request
	) {
		OAuthProvider oauthProvider = OAuthProvider.fromPath(provider)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
		accountLinkService.linkFromMyPage(userId, oauthProvider, request.code(), request.password());
		return userService.getMe(userId);
	}

	/** AUTH-06 회원 탈퇴 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@CurrentUser Long userId) {
		userService.withdraw(userId);
	}
}
