package com.mockdata.zipfit.tour.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * STUN·TURN 서버 (TOUR-02 보조). iceServers는 RTCPeerConnection 설정에 그대로 넣는다.
 * TURN 자격증명은 expiresAt까지만 쓸 수 있으니 그 전에 다시 받는다 (NFR-009).
 */
public record IceServersResponse(List<IceServer> iceServers, Instant expiresAt) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record IceServer(List<String> urls, String username, String credential) {
	}
}
