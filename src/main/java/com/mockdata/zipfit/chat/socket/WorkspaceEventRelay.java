package com.mockdata.zipfit.chat.socket;

import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.websocket.WsCloseCodes;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import lombok.RequiredArgsConstructor;
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
