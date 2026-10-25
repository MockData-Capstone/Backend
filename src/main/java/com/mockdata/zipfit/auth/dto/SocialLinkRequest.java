package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** AUTH-08 내 정보 화면에서 연동: 소셜 인가 코드 + 기존 계정 비밀번호 */
public record SocialLinkRequest(@NotBlank String code, @NotBlank String password) {
}
