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
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "접근 토큰이 만료되었습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// AUTH
	UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
	OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다. 다시 시도해주세요."),
	INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "가입 정보가 만료되었습니다. 다시 로그인해주세요."),
	PRIVACY_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "개인정보 수집·이용에 동의해야 가입할 수 있습니다."),
	ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 계정입니다. 다시 로그인해주세요."),
	DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해주세요."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다."),
	PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "비밀번호 없이 소셜 로그인으로 가입한 회원입니다. 소셜 로그인을 이용해주세요."),
	INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "재설정 링크가 만료되었거나 이미 사용되었습니다. 다시 요청해주세요."),

	// AUTH-08 계정 연동
	ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "같은 이메일로 가입된 계정이 있습니다. 비밀번호를 확인해 계정을 연동해주세요."),
	INVALID_LINK_TOKEN(HttpStatus.UNAUTHORIZED, "연동 정보가 만료되었습니다. 다시 로그인해주세요."),
	SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 회원에 연동된 소셜 계정입니다."),
	PROVIDER_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 연동된 로그인 방식입니다."),

	// AUTH-05 프로필 이미지
	INVALID_IMAGE(HttpStatus.BAD_REQUEST, "jpg, png, webp 이미지만 올릴 수 있습니다."),
	IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하만 올릴 수 있습니다."),

	// AUTH-06 회원 탈퇴
	HOST_DELEGATION_REQUIRED(HttpStatus.CONFLICT, "다른 멤버가 있는 Workspace의 호스트는 호스트를 위임한 뒤 탈퇴할 수 있습니다."),

	// WS Workspace
	WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace를 찾을 수 없습니다."),
	NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "Workspace 멤버만 이용할 수 있습니다."),
	NOT_WORKSPACE_HOST(HttpStatus.FORBIDDEN, "호스트만 할 수 있습니다."),

	// MEM 멤버·초대
	INVITATION_EXPIRED(HttpStatus.GONE, "만료된 초대 링크입니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace 멤버가 아닙니다."),
	CANNOT_DELEGATE_TO_SELF(HttpStatus.BAD_REQUEST, "본인에게는 호스트를 위임할 수 없습니다."),
	CANNOT_KICK_HOST(HttpStatus.BAD_REQUEST, "호스트 본인은 내보낼 수 없습니다."),
	HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "호스트는 다른 멤버에게 호스트를 위임한 뒤 나갈 수 있습니다."),
	LAST_MEMBER_CANNOT_LEAVE(HttpStatus.CONFLICT, "혼자 남은 Workspace는 나갈 수 없습니다. Workspace를 삭제해주세요.");

	private final HttpStatus status;
	private final String message;
}
