package com.mockdata.zipfit.notification.socket;

import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsMessage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** 회원별 알림 소켓 연결. 여러 탭·기기로 접속하면 모두에게 보낸다. */
@Component
@RequiredArgsConstructor
public class NotificationSessionRegistry {

	private final SocketSessions socketSessions;
	private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

	public void register(Long userId, WebSocketSession session) {
		sessionsByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
	}

	public void unregister(Long userId, WebSocketSession session) {
		sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
			sessions.removeIf(s -> s.getId().equals(session.getId()));
			return sessions.isEmpty() ? null : sessions;
		});
	}

	public boolean isConnected(Long userId) {
		return !sessionsByUser.getOrDefault(userId, Set.of()).isEmpty();
	}

	public void send(Long userId, WsMessage message) {
		sessionsByUser.getOrDefault(userId, Set.of()).forEach(session -> socketSessions.send(session, message));
	}
}
