package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** AUTH-07 메일 링크의 토큰으로 새 비밀번호 설정 */
public record PasswordResetConfirmRequest(@NotBlank String token, @NotNull @ValidPassword String newPassword) {
}
