package com.mockdata.zipfit.auth.controller;

import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.service.UserService;
import com.mockdata.zipfit.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

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

	/** AUTH-05 프로필 이미지를 기본 이미지로 되돌리기 */
	@DeleteMapping("/profile-image")
	public UserResponse resetProfileImage(@CurrentUser Long userId) {
		return userService.resetProfileImage(userId);
	}

	/** AUTH-06 회원 탈퇴 */
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@CurrentUser Long userId) {
		userService.withdraw(userId);
	}
}
