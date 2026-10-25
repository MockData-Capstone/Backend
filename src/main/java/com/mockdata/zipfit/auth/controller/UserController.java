package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.auth.dto.ChangePasswordRequest;
import com.mockdata.zipfit.auth.dto.SocialLinkRequest;
import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.service.AccountLinkService;
import com.mockdata.zipfit.auth.service.PasswordService;
import com.mockdata.zipfit.auth.service.UserService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final PasswordService passwordService;
	private final AccountLinkService accountLinkService;

	/** AUTH-04 내 정보 조회 */
	@GetMapping
	public UserResponse getMe(@CurrentUser Long userId) {
		return userService.getMe(userId);
	}

	/** AUTH-05 내 정보 수정 (닉네임) */
	@PatchMapping
	public UserResponse updateMe(@CurrentUser Long userId, @Valid @RequestBody UpdateUserRequest request) {
		return userService.updateMe(userId, request);
	}

	/** AUTH-05 비밀번호 변경 (자체 가입 회원만) */
	@PatchMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@CurrentUser Long userId, @Valid @RequestBody ChangePasswordRequest request) {
		passwordService.changePassword(userId, request.currentPassword(), request.newPassword());
	}

	/** AUTH-05 프로필 이미지 변경 (multipart, 필드명 image) */
	@PutMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public UserResponse updateProfileImage(@CurrentUser Long userId, @RequestPart("image") MultipartFile image) {
		return userService.updateProfileImage(userId, image);
	}

	/** AUTH-05 프로필 이미지를 기본 이미지로 되돌리기 */
	@DeleteMapping("/profile-image")
	public UserResponse resetProfileImage(@CurrentUser Long userId) {
		return userService.resetProfileImage(userId);
	}

	/** AUTH-08 내 정보 화면에서 소셜 계정 연동 (자체 가입 회원만) */
	@PostMapping("/social/{provider:kakao|google}")
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
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@CurrentUser Long userId) {
		userService.withdraw(userId);
	}
}
