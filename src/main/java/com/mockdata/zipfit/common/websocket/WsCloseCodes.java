package com.mockdata.zipfit.common.websocket;

import org.springframework.web.socket.CloseStatus;

/**
 * 서버가 연결을 닫을 때 쓰는 close code. 브라우저는 핸드셰이크 거절 사유(HTTP 상태)를 읽을 수 없어서,
 * 연결을 받은 뒤 사유를 담아 닫는다.
 */
public final class WsCloseCodes {

	/** 토큰이 없거나 유효하지 않다. 다시 로그인한다. */
	public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "UNAUTHORIZED");
	/** 접근 토큰이 만료됐다. 재발급받아 다시 접속한다. */
	public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4402, "AUTH_TOKEN_EXPIRED");
	/** 멤버(참여자)가 아니거나, 나가거나 내보내졌다. */
	public static final CloseStatus FORBIDDEN = new CloseStatus(4403, "FORBIDDEN");
	/** 대상(Workspace·투어)이 없거나 삭제·종료됐다. */
	public static final CloseStatus NOT_FOUND = new CloseStatus(4404, "NOT_FOUND");
	/** 같은 회원이 같은 방에 새로 접속해 이전 연결을 닫는다. 이 연결로는 다시 접속하지 않는다. */
	public static final CloseStatus REPLACED = new CloseStatus(4409, "REPLACED");
	/** 접속 주소의 쿼리 파라미터가 잘못됐다. */
	public static final CloseStatus BAD_REQUEST = new CloseStatus(4400, "BAD_REQUEST");

	private WsCloseCodes() {
	}
}
