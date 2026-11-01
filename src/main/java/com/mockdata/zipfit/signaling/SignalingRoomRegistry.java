package com.mockdata.zipfit.signaling;

import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsMessage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * 투어별 시그널링 방. 한 회원은 방에 연결 1개만 둔다(새로 접속하면 이전 연결을 닫는다).
 * JOIN을 보낸 연결만 중계 대상이다.
 */
@Component
@RequiredArgsConstructor
public class SignalingRoomRegistry {

	static final String JOINED = "signalingJoined";

	private final SocketSessions socketSessions;
	private final Map<Long, Map<Long, WebSocketSession>> rooms = new ConcurrentHashMap<>();

	/** 등록하고, 같은 회원의 이전 연결이 있으면 돌려준다. */
	public Optional<WebSocketSession> register(Long tourId, Long userId, WebSocketSession session) {
		return Optional.ofNullable(rooms.computeIfAbsent(tourId, id -> new ConcurrentHashMap<>()).put(userId, session));
	}

	/** 그 연결이 아직 방에 등록된 연결일 때만 뺀다(새 연결로 바뀌었으면 그대로 둔다). */
	public boolean unregister(Long tourId, Long userId, WebSocketSession session) {
		Map<Long, WebSocketSession> room = rooms.get(tourId);
		WebSocketSession current = room == null ? null : room.get(userId);
		if (current == null || !current.getId().equals(session.getId())) {
			return false;
		}
		boolean removed = room.remove(userId, current);
		rooms.computeIfPresent(tourId, (id, r) -> r.isEmpty() ? null : r);
		return removed;
	}

	public Optional<WebSocketSession> find(Long tourId, Long userId) {
		return Optional.ofNullable(rooms.getOrDefault(tourId, Map.of()).get(userId));
	}

	public boolean isJoined(WebSocketSession session) {
		return Boolean.TRUE.equals(session.getAttributes().get(JOINED));
	}

	public void markJoined(WebSocketSession session) {
		session.getAttributes().put(JOINED, true);
	}

	/** JOIN한 다른 참여자에게 보낸다. */
	public void broadcastExcept(Long tourId, Long exceptUserId, WsMessage message) {
		rooms.getOrDefault(tourId, Map.of()).forEach((userId, session) -> {
			if (!userId.equals(exceptUserId) && isJoined(session)) {
				socketSessions.send(session, message);
			}
		});
	}

	public void broadcast(Long tourId, WsMessage message) {
		broadcastExcept(tourId, null, message);
	}

	public void close(Long tourId, Long userId, CloseStatus status) {
		find(tourId, userId).ifPresent(session -> socketSessions.close(session, status));
	}

	public void closeAll(Long tourId, CloseStatus status) {
		rooms.getOrDefault(tourId, Map.of()).values().forEach(session -> socketSessions.close(session, status));
	}
}
