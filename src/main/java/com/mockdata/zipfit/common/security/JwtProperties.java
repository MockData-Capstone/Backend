package com.mockdata.zipfit.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret                HMAC-SHA 서명 키. 32바이트(256bit) 이상이어야 한다.
 * @param signupTokenExpiration 소셜 인증 후 가입(동의·닉네임 입력)이나 계정 연동을 마칠 때까지 쓰는 임시 토큰의 수명
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
	String secret,
	@DefaultValue("30m") Duration accessTokenExpiration,
	@DefaultValue("14d") Duration refreshTokenExpiration,
	@DefaultValue("10m") Duration signupTokenExpiration
) {
}
