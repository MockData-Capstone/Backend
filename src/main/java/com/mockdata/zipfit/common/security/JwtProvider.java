package com.mockdata.zipfit.common.security;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.oauth.OAuthUserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

	private static final String CLAIM_TYPE = "typ";
	private static final String CLAIM_PROVIDER = "provider";
	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_PROFILE_IMAGE = "profileImageUrl";
	private static final String TYPE_ACCESS = "access";
	private static final String TYPE_SIGNUP = "signup";

	private final JwtProperties properties;
	private final SecretKey key;

	public JwtProvider(JwtProperties properties) {
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(Long userId, Instant now) {
		return Jwts.builder()
			.subject(userId.toString())
			.claim(CLAIM_TYPE, TYPE_ACCESS)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.accessTokenExpiration())))
			.signWith(key)
			.compact();
	}

	/** 접근 토큰을 검증하고 회원 ID를 반환한다. */
	public AccessTokenResult parseAccessToken(String token) {
		try {
			Claims claims = parse(token);
			if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
				return new AccessTokenResult.Invalid();
			}
			return new AccessTokenResult.Valid(Long.valueOf(claims.getSubject()));
		} catch (ExpiredJwtException e) {
			return new AccessTokenResult.Expired();
		} catch (JwtException | IllegalArgumentException e) {
			return new AccessTokenResult.Invalid();
		}
	}

	public String createSignupToken(OAuthUserInfo info) {
		Instant now = Instant.now();
		return Jwts.builder()
			.subject(info.providerUserId())
			.claim(CLAIM_TYPE, TYPE_SIGNUP)
			.claim(CLAIM_PROVIDER, info.provider().name())
			.claim(CLAIM_EMAIL, info.email())
			.claim(CLAIM_PROFILE_IMAGE, info.profileImageUrl())
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(properties.signupTokenExpiration())))
			.signWith(key)
			.compact();
	}

	/** 가입 토큰을 검증해 소셜 인증 결과를 돌려준다. 유효하지 않으면 빈 값. */
	public Optional<OAuthUserInfo> parseSignupToken(String token) {
		try {
			Claims claims = parse(token);
			if (!TYPE_SIGNUP.equals(claims.get(CLAIM_TYPE, String.class))) {
				return Optional.empty();
			}
			return Optional.of(new OAuthUserInfo(
				OAuthProvider.valueOf(claims.get(CLAIM_PROVIDER, String.class)),
				claims.getSubject(),
				claims.get(CLAIM_EMAIL, String.class),
				null,
				claims.get(CLAIM_PROFILE_IMAGE, String.class)
			));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private Claims parse(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
