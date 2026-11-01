package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** WS-01 */
public record CreateWorkspaceRequest(
	@NotBlank @Size(max = Workspace.NAME_MAX_LENGTH) String name,
	@Size(max = Workspace.DESCRIPTION_MAX_LENGTH) String description
) {
}
