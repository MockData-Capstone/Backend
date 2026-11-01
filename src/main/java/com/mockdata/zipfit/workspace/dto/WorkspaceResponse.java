package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.Workspace;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import java.time.Instant;

/** WS-02 상세. myRole로 호스트 전용 버튼을 보여줄지 정한다. */
public record WorkspaceResponse(
	Long id,
	String name,
	String description,
	MemberProfile host,
	long memberCount,
	WorkspaceRole myRole,
	Instant createdAt
) {

	public static WorkspaceResponse of(Workspace workspace, MemberProfile host, long memberCount, WorkspaceRole myRole) {
		return new WorkspaceResponse(
			workspace.getId(), workspace.getName(), workspace.getDescription(), host, memberCount, myRole,
			workspace.getCreatedAt()
		);
	}
}
