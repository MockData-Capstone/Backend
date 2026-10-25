package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import lombok.Getter;

/** 소셜 로그인 시 같은 이메일의 자체 가입 회원이 있어 연동이 필요하다. 409와 함께 연동용 토큰을 내려준다. */
@Getter
public class AccountLinkRequiredException extends BusinessException {

	private final String linkToken;
	private final String email;

	public AccountLinkRequiredException(String linkToken, String email) {
		super(ErrorCode.ACCOUNT_LINK_REQUIRED);
		this.linkToken = linkToken;
		this.email = email;
	}
}
