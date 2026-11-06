package com.mockdata.zipfit.chat.socket;

import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsMessage;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** Workspace별로 열린 Workspace 소켓 연결. 같은 회원이 여러 탭으로 접속하면 연결이 여러 개다. */
@Component
@RequiredArgsConstructor
public class WorkspaceSessionRegistry {

	private final SocketSessions socketSessions;
	private final Map<Long, Set<WebSocketSession>> sessionsByWorkspace = new ConcurrentHashMap<>();

	public void register(Long workspaceId, WebSocketSession session) {
		sessionsByWorkspace.computeIfAbsent(workspaceId, id -> ConcurrentHashMap.newKeySet()).add(session);
	}

	public void unregister(Long workspaceId, WebSocketSession session) {
		sessionsByWorkspace.computeIfPresent(workspaceId, (id, sessions) -> {
			sessions.removeIf(s -> s.getId().equals(session.getId()));
			return sessions.isEmpty() ? null : sessions;
		});
	}

	public void broadcast(Long workspaceId, WsMessage message) {
		sessionsOf(workspaceId).forEach(session -> socketSessions.send(session, message));
	}

	public void closeUser(Long workspaceId, Long userId, CloseStatus status) {
		sessionsOf(workspaceId).stream()
			.filter(session -> Objects.equals(SocketSessions.userId(session), userId))
			.forEach(session -> socketSessions.close(session, status));
	}

	public void closeAll(Long workspaceId, CloseStatus status) {
		sessionsOf(workspaceId).forEach(session -> socketSessions.close(session, status));
	}

	/** 회원이 지금 이 Workspace 화면(소켓)을 열고 있는지. 새 채팅 알림을 보낼지 정할 때 쓴다. */
	public boolean isConnected(Long workspaceId, Long userId) {
		return sessionsOf(workspaceId).stream()
			.anyMatch(session -> Objects.equals(SocketSessions.userId(session), userId));
	}

	private Set<WebSocketSession> sessionsOf(Long workspaceId) {
		return sessionsByWorkspace.getOrDefault(workspaceId, Set.of());
	}
}
