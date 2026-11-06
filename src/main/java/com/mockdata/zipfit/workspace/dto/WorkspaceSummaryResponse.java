package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import java.time.Instant;

/** WS-02 목록 항목 */
public record WorkspaceSummaryResponse(
	Long id,
	String name,
	long memberCount,
	WorkspaceRole myRole,
	Instant lastActivityAt
) {
}
