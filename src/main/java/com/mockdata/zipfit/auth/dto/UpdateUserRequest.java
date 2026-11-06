package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.User;
import jakarta.validation.constraints.Size;

/**
 * AUTH-05 내 정보 수정. 전달된 필드만 바꾼다.
 *
 * @param nickname        새 닉네임
 * @param currentPassword 비밀번호를 바꿀 때 지금 비밀번호 (자체 가입 회원만)
 * @param newPassword     새 비밀번호
 */
public record UpdateUserRequest(
	@Size(min = User.NICKNAME_MIN_LENGTH, max = User.NICKNAME_MAX_LENGTH) String nickname,
	String currentPassword,
	@ValidPassword String newPassword
) {
}
