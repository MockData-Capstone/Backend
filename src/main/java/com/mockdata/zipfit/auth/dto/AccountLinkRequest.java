package com.mockdata.zipfit.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** AUTH-08 소셜 로그인 중 연동: 409 ACCOUNT_LINK_REQUIRED 응답의 linkToken + 기존 계정 비밀번호 */
public record AccountLinkRequest(@NotBlank String linkToken, @NotBlank String password) {
}
