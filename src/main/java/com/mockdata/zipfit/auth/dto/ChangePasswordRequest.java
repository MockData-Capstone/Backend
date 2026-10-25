package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** AUTH-05 비밀번호 변경 (자체 가입 회원만) */
public record ChangePasswordRequest(@NotBlank String currentPassword, @ValidPassword String newPassword) {
}
