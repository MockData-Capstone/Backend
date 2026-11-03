package com.mockdata.zipfit.chat.socket;

import com.mockdata.zipfit.chat.dto.ChatSendRequest;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsCloseCodes;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Workspace 소켓 /ws/workspaces?workspaceId={id}&token={accessToken} (CHAT-01·02).
 * 채팅 메시지와 Workspace 실시간 동기화 이벤트(멤버·Workspace 변경, 분석 진행 등)를 이 연결로 주고받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceSocketHandler extends TextWebSocketHandler {

	public static final String CHAT_SEND = "CHAT_SEND";
	private static final String WORKSPACE_ID = "workspaceId";

	private final SocketSessions socketSessions;
	private final WorkspaceSessionRegistry registry;
	private final WorkspaceAccess workspaceAccess;
	private final ChatService chatService;
	private final JsonMapper jsonMapper;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public void afterConnectionEstablished(WebSocketSession raw) {
		Optional<CloseStatus> rejection = SocketSessions.rejection(raw);
		if (rejection.isPresent()) {
			socketSessions.close(raw, rejection.get());
			return;
		}
		Optional<Long> workspaceId = SocketSessions.longQueryParam(raw, WORKSPACE_ID);
		if (workspaceId.isEmpty()) {
			socketSessions.close(raw, WsCloseCodes.BAD_REQUEST);
			return;
		}
		try {
			workspaceAccess.requireMember(workspaceId.get(), SocketSessions.userId(raw));
		} catch (BusinessException e) {
			socketSessions.close(raw, closeStatusOf(e));
			return;
		}
		raw.getAttributes().put(WORKSPACE_ID, workspaceId.get());
		WebSocketSession session = socketSessions.track(raw);
		if (registry.register(workspaceId.get(), session)) {
			eventPublisher.publishEvent(new WorkspaceEvent(workspaceId.get(), WorkspaceEventTypes.MEMBER_ONLINE,
				new MemberOnlineChanged(SocketSessions.userId(session))));
		}
	}

	@Override
	protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
		WebSocketSession session = socketSessions.find(raw).orElse(null);
		if (session == null) {
			return;
		}
		Incoming incoming;
		try {
			incoming = jsonMapper.readValue(message.getPayload(), Incoming.class);
		} catch (JacksonException e) {
			sendError(session, ErrorCode.INVALID_SOCKET_MESSAGE, null);
			return;
		}
		if (CHAT_SEND.equals(incoming.type())) {
			handleChatSend(session, incoming.data());
		} else {
			sendError(session, ErrorCode.UNSUPPORTED_SOCKET_MESSAGE, null);
		}
	}

	private void handleChatSend(WebSocketSession session, JsonNode data) {
		ChatSendRequest request;
		try {
			request = data == null ? null : jsonMapper.treeToValue(data, ChatSendRequest.class);
		} catch (JacksonException e) {
			request = null;
		}
		if (request == null) {
			sendError(session, ErrorCode.INVALID_SOCKET_MESSAGE, null);
			return;
		}
		Long workspaceId = (Long) session.getAttributes().get(WORKSPACE_ID);
		try {
			ChatService.SendResult result = chatService.send(
				workspaceId, SocketSessions.userId(session), request.clientMessageId(), request.content()
			);
			// 새로 저장한 메시지는 커밋 뒤 멤버 전원에게 간다. 재전송이면 보낸 연결에만 확정 메시지를 돌려준다.
			if (!result.created()) {
				socketSessions.send(session, WsMessage.of(ChatService.CHAT_MESSAGE, result.message()));
			}
		} catch (BusinessException e) {
			socketSessions.send(session,
				WsMessage.error(e.getErrorCode().name(), e.getMessage(), request.clientMessageId()));
			if (e.getErrorCode() == ErrorCode.NOT_WORKSPACE_MEMBER || e.getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND) {
				socketSessions.close(session, closeStatusOf(e));
			}
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
		Long workspaceId = (Long) raw.getAttributes().get(WORKSPACE_ID);
		if (workspaceId != null && registry.unregister(workspaceId, raw)) {
			eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.MEMBER_OFFLINE,
				new MemberOnlineChanged(SocketSessions.userId(raw))));
		}
		socketSessions.untrack(raw);
	}

	private void sendError(WebSocketSession session, ErrorCode errorCode, String requestId) {
		socketSessions.send(session, WsMessage.error(errorCode.name(), errorCode.getMessage(), requestId));
	}

	private static CloseStatus closeStatusOf(BusinessException e) {
		return e.getErrorCode() == ErrorCode.WORKSPACE_NOT_FOUND ? WsCloseCodes.NOT_FOUND : WsCloseCodes.FORBIDDEN;
	}

	/** 클라이언트가 보내는 메시지. data는 type에 따라 다르다. */
	record Incoming(String type, JsonNode data) {
	}

	/** MEMBER_ONLINE/MEMBER_OFFLINE 통지의 data. 같은 회원이 여러 탭이면 첫 연결·마지막 해제 때만 보낸다. */
	public record MemberOnlineChanged(Long userId) {
	}
}
