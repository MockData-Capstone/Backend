package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** AUTH-07 비밀번호 재설정 메일 요청 */
public record PasswordResetRequest(@NotBlank @Email String email) {
}
