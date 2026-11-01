package com.mockdata.zipfit.common.event;

/**
 * Workspace를 지우기 직전에 같은 트랜잭션 안에서 동기로 발행한다.
 * 하위 데이터(채팅, 후보지, 투표 등)를 가진 도메인이 받아서 자기 데이터를 지운다 (WS-04).
 */
public record WorkspaceDeletingEvent(Long workspaceId) {
}
