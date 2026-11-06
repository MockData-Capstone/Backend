package com.mockdata.zipfit.common.websocket;

import java.time.Instant;

/**
 * 모든 WebSocket 메시지의 공통 형식.
 * 서버 → 클라이언트: {"type": "CHAT_MESSAGE", "data": {...}, "sentAt": "..."}
 * 클라이언트 → 서버: {"type": "CHAT_SEND", "data": {...}} (sentAt은 보내지 않아도 된다)
 */
public record WsMessage(String type, Object data, Instant sentAt) {

	public static final String ERROR = "ERROR";

	public static WsMessage of(String type, Object data) {
		return new WsMessage(type, data, Instant.now());
	}

	/** 요청을 처리하지 못했을 때 그 연결에만 보낸다. requestId는 실패한 요청을 가리킨다(예: clientMessageId). */
	public static WsMessage error(String code, String message, String requestId) {
		return of(ERROR, new Error(code, message, requestId));
	}

	public record Error(String code, String message, String requestId) {
	}
}
