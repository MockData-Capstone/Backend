package com.mockdata.zipfit.tour.service;

import com.mockdata.zipfit.tour.dto.IceServersResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * STUN·TURN 서버와 시간 제한 TURN 자격증명을 발급한다 (NFR-009).
 * coturn의 use-auth-secret 방식: username = "만료 시각(초):회원ID", credential = Base64(HMAC-SHA1(secret, username)).
 * 자격증명을 클라이언트 코드에 고정하면 외부에서 TURN을 무단으로 쓸 수 있으므로 요청할 때마다 짧게 발급한다.
 * TURN을 설정하지 않으면 STUN만 준다.
 */
@Component
public class IceServerIssuer {

	private final List<String> stunUrls;
	private final List<String> turnUrls;
	private final String turnSecret;
	private final Duration ttl;

	public IceServerIssuer(
		@Value("${webrtc.stun-urls:stun:stun.l.google.com:19302}") List<String> stunUrls,
		@Value("${webrtc.turn-urls:}") List<String> turnUrls,
		@Value("${webrtc.turn-secret:}") String turnSecret,
		@Value("${webrtc.turn-ttl:6h}") Duration ttl
	) {
		this.stunUrls = stunUrls.stream().filter(url -> !url.isBlank()).toList();
		this.turnUrls = turnUrls.stream().filter(url -> !url.isBlank()).toList();
		this.turnSecret = turnSecret;
		this.ttl = ttl;
	}

	public IceServersResponse issue(Long userId) {
		Instant expiresAt = Instant.now().plus(ttl);
		List<IceServersResponse.IceServer> servers = new ArrayList<>();
		if (!stunUrls.isEmpty()) {
			servers.add(new IceServersResponse.IceServer(stunUrls, null, null));
		}
		if (!turnUrls.isEmpty() && !turnSecret.isBlank()) {
			String username = expiresAt.getEpochSecond() + ":" + userId;
			servers.add(new IceServersResponse.IceServer(turnUrls, username, hmacSha1(username)));
		}
		return new IceServersResponse(servers, expiresAt);
	}

	private String hmacSha1(String value) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}
}
