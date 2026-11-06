package com.mockdata.zipfit.common.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모든 WebSocket 연결(Workspace·알림·시그널링)의 공통 관리.
 * 여러 스레드가 같은 연결로 보내도 안전하게 감싸고, 접근 토큰이 만료된 연결은 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketSessions {

	public static final String USER_ID = "userId";
	public static final String TOKEN_EXPIRES_AT = "tokenExpiresAt";
	public static final String REJECT = "reject";
	public static final String QUERY = "query";

	private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
	private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

	private final JsonMapper jsonMapper;
	private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	/** 연결을 등록하고, 동시에 보내도 안전한 세션을 돌려준다. 이후에는 돌려받은 세션으로 보낸다. */
	public WebSocketSession track(WebSocketSession session) {
		WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(
			session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES
		);
		sessions.put(session.getId(), concurrent);
		return concurrent;
	}

	public void untrack(WebSocketSession session) {
		sessions.remove(session.getId());
	}

	/** 핸들러 콜백이 넘겨준 원래 세션에 대응하는, 동시에 보내도 안전한 세션 */
	public Optional<WebSocketSession> find(WebSocketSession session) {
		return Optional.ofNullable(sessions.get(session.getId()));
	}

	public void send(WebSocketSession session, WsMessage message) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(message)));
		} catch (IOException | IllegalStateException e) {
			log.debug("WebSocket send failed: session={}", session.getId(), e);
			close(session, CloseStatus.SESSION_NOT_RELIABLE);
		}
	}

	public void close(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException e) {
			log.debug("WebSocket close failed: session={}", session.getId(), e);
		}
	}

	/** 접근 토큰이 만료된 연결을 닫는다. 클라이언트는 재발급받아 다시 접속한다. */
	@Scheduled(fixedDelayString = "${websocket.expiry-check-interval:30s}")
	public void closeExpired() {
		Instant now = Instant.now();
		sessions.values().forEach(session -> {
			Instant expiresAt = (Instant) session.getAttributes().get(TOKEN_EXPIRES_AT);
			if (expiresAt != null && !expiresAt.isAfter(now)) {
				close(session, WsCloseCodes.TOKEN_EXPIRED);
			}
		});
	}

	public static Long userId(WebSocketSession session) {
		return (Long) session.getAttributes().get(USER_ID);
	}

	/** 핸드셰이크에서 인증에 실패했으면 닫을 사유 */
	public static Optional<CloseStatus> rejection(WebSocketSession session) {
		return Optional.ofNullable((CloseStatus) session.getAttributes().get(REJECT));
	}

	@SuppressWarnings("unchecked")
	public static Optional<String> queryParam(WebSocketSession session, String name) {
		Map<String, String> query = (Map<String, String>) session.getAttributes().getOrDefault(QUERY, Map.of());
		return Optional.ofNullable(query.get(name));
	}

	/** 쿼리 파라미터를 Long으로 읽는다. 없거나 숫자가 아니면 empty다. */
	public static Optional<Long> longQueryParam(WebSocketSession session, String name) {
		return queryParam(session, name).flatMap(value -> {
			try {
				return Optional.of(Long.valueOf(value));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		});
	}
}
