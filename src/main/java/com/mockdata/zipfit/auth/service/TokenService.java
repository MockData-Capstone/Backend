package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.RefreshToken;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.RefreshTokenRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.JwtProperties;
import com.mockdata.zipfit.common.security.JwtProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AUTH-09 토큰 재발급·로그아웃.
 * 접근 토큰은 짧은 수명의 JWT, 재발급 토큰은 DB에 해시로 저장하는 랜덤 문자열이다.
 * 재발급할 때마다 재발급 토큰도 새로 바꾼다(rotation).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;
	private final RefreshTokenRepository refreshTokenRepository;

	@Transactional
	public TokenResponse issue(User user) {
		Instant now = Instant.now();
		refreshTokenRepository.deleteExpiredByUserId(user.getId(), now);

		String refreshToken = generateRefreshToken();
		refreshTokenRepository.save(new RefreshToken(user, hash(refreshToken), now.plus(jwtProperties.refreshTokenExpiration())));
		return new TokenResponse(
			jwtProvider.createAccessToken(user.getId(), now),
			refreshToken,
			jwtProperties.accessTokenExpiration().toSeconds()
		);
	}

	@Transactional
	public TokenResponse reissue(String refreshToken) {
		RefreshToken saved = refreshTokenRepository.findByTokenHash(hash(refreshToken))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
		if (saved.isExpired(Instant.now())) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		// 동시에 같은 토큰으로 재발급하면 먼저 지운 요청만 성공한다.
		if (refreshTokenRepository.deleteByIdReturningCount(saved.getId()) == 0) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		return issue(saved.getUser());
	}

	/** 로그아웃. 이미 없는 토큰이어도 성공으로 처리한다. */
	@Transactional
	public void revoke(String refreshToken) {
		refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
	}

	@Transactional
	public void revokeAll(Long userId) {
		refreshTokenRepository.deleteAllByUserId(userId);
	}

	private String generateRefreshToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
