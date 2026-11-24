package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * WS-03 전달된 필드만 바꾼다. description을 ""로 보내면 설명을 지운다.
 *
 * @param name 보내면 비울 수 없다
 */
public record UpdateWorkspaceRequest(
	@Size(max = Workspace.NAME_MAX_LENGTH) @Pattern(regexp = ".*\\S.*", message = "이름은 비울 수 없습니다.") String name,
	@Size(max = Workspace.DESCRIPTION_MAX_LENGTH) String description
) {
}
