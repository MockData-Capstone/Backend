package com.mockdata.zipfit.common.event;

/**
 * Workspace 소켓(/ws/workspaces)에 그 Workspace로 접속한 모든 연결에 보낼 실시간 이벤트.
 * 트랜잭션이 커밋된 뒤에 전달된다. 내보내진 멤버도 연결이 끊기기 전에 MEMBER_KICKED를 받는다.
 *
 * @param type WebSocket 메시지 type (예: MEMBER_JOINED)
 * @param data WebSocket 메시지 data
 */
public record WorkspaceEvent(Long workspaceId, String type, Object data) {
}
