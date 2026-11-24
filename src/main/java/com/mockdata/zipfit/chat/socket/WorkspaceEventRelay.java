package com.mockdata.zipfit.chat.socket;

import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.websocket.WsCloseCodes;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 변경을 Workspace 소켓에 연결된 멤버 전원에게 보낸다.
 * 나가거나 내보내진 회원은 통지를 받은 뒤 연결이 닫히고(4403), Workspace가 삭제되면 모든 연결이 닫힌다(4404).
 */
@Component
@RequiredArgsConstructor
public class WorkspaceEventRelay {

	private final WorkspaceSessionRegistry registry;

	/**
	 * 커밋 뒤 리스너 중 가장 먼저 돈다. 알림 기록(NotificationTrigger)처럼 DB를 더 쓰는 리스너가 같은 스레드에서
	 * 먼저 돌면 그만큼 채팅·후보지 통지가 늦게 도착한다.
	 */
	@Order(Ordered.HIGHEST_PRECEDENCE)
	@TransactionalEventListener(fallbackExecution = true)
	public void relay(WorkspaceEvent event) {
		registry.broadcast(event.workspaceId(), WsMessage.of(event.type(), event.data()));
		switch (event.type()) {
			case WorkspaceEventTypes.MEMBER_KICKED, WorkspaceEventTypes.MEMBER_LEFT -> registry.closeUser(
				event.workspaceId(), ((MemberService.MemberRemoved) event.data()).userId(), WsCloseCodes.FORBIDDEN
			);
			case WorkspaceEventTypes.WORKSPACE_DELETED -> registry.closeAll(event.workspaceId(), WsCloseCodes.NOT_FOUND);
			default -> {
			}
		}
	}
}
