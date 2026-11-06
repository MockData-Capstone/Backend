package com.mockdata.zipfit.notification.socket;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsMessage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 알림 소켓 /ws/notifications?token={accessToken} (NOTI-01).
 * 서버가 NOTIFICATION_CREATED, NOTIFICATION_READ를 보내기만 한다.
 * 보고 있는 Workspace는 Workspace 소켓 접속으로 판단하므로 VIEWING_WORKSPACE는 받지 않는다(API명세서에서 생략 가능으로 둔 행).
 */
@Component
@RequiredArgsConstructor
public class NotificationSocketHandler extends TextWebSocketHandler {

	private final SocketSessions socketSessions;
	private final NotificationSessionRegistry registry;

	@Override
	public void afterConnectionEstablished(WebSocketSession raw) {
		Optional<CloseStatus> rejection = SocketSessions.rejection(raw);
		if (rejection.isPresent()) {
			socketSessions.close(raw, rejection.get());
			return;
		}
		registry.register(SocketSessions.userId(raw), socketSessions.track(raw));
	}

	@Override
	protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
		socketSessions.find(raw).ifPresent(session -> socketSessions.send(session, WsMessage.error(
			ErrorCode.UNSUPPORTED_SOCKET_MESSAGE.name(), ErrorCode.UNSUPPORTED_SOCKET_MESSAGE.getMessage(), null)));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
		Long userId = SocketSessions.userId(raw);
		if (userId != null) {
			registry.unregister(userId, raw);
		}
		socketSessions.untrack(raw);
	}
}
