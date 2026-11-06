package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.common.exception.ErrorCode;

/**
 * 소셜 로그인 시 같은 이메일의 자체 가입 회원이 있을 때의 409 응답 (AUTH-02, AUTH-08).
 * 공통 에러 형식(code, message)에 연동용 단기 토큰과 연동할 계정 이메일을 더한다.
 */
public record AccountLinkRequiredResponse(String code, String message, String linkToken, String email) {

	public static AccountLinkRequiredResponse of(String linkToken, String email) {
		ErrorCode errorCode = ErrorCode.ACCOUNT_LINK_REQUIRED;
		return new AccountLinkRequiredResponse(errorCode.name(), errorCode.getMessage(), linkToken, email);
	}
}
