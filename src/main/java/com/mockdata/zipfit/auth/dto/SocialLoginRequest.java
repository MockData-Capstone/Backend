package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 프론트가 소셜 로그인 리다이렉트로 받은 인가 코드 */
public record SocialLoginRequest(@NotBlank String code) {
}
