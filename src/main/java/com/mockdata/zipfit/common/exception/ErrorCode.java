package com.mockdata.zipfit.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "접근 토큰이 만료되었습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// AUTH
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
	OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해주세요."),
	INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "가입 정보가 만료되었습니다. 다시 로그인해주세요."),
	PRIVACY_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "개인정보 수집·이용에 동의해야 가입할 수 있습니다."),
	ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다. 다시 로그인해주세요."),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String message;
}
