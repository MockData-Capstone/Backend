package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** AUTH-03 자체 로그인 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
