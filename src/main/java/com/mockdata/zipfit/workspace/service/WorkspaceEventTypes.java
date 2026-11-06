package com.mockdata.zipfit.workspace.service;

/** Workspace·멤버 변경 통지의 WebSocket 메시지 type */
public final class WorkspaceEventTypes {

	public static final String WORKSPACE_UPDATED = "WORKSPACE_UPDATED";
	public static final String WORKSPACE_DELETED = "WORKSPACE_DELETED";
	public static final String MEMBER_JOINED = "MEMBER_JOINED";
	public static final String MEMBER_LEFT = "MEMBER_LEFT";
	public static final String MEMBER_KICKED = "MEMBER_KICKED";
	public static final String HOST_CHANGED = "HOST_CHANGED";

	private WorkspaceEventTypes() {
	}
}
