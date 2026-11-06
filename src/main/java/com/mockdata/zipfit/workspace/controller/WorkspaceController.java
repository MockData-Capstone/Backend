package com.mockdata.zipfit.workspace.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.workspace.dto.CreateWorkspaceRequest;
import com.mockdata.zipfit.workspace.dto.UpdateWorkspaceRequest;
import com.mockdata.zipfit.workspace.dto.WorkspaceResponse;
import com.mockdata.zipfit.workspace.dto.WorkspaceSummaryResponse;
import com.mockdata.zipfit.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

	private final WorkspaceService workspaceService;

	/** WS-01 Workspace 생성 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WorkspaceResponse create(@CurrentUser Long userId, @Valid @RequestBody CreateWorkspaceRequest request) {
		return workspaceService.create(userId, request);
	}

	/** WS-02 내 Workspace 목록 (최근 활동순) */
	@GetMapping
	public ListResponse<WorkspaceSummaryResponse> getMyWorkspaces(@CurrentUser Long userId) {
		return new ListResponse<>(workspaceService.getMyWorkspaces(userId));
	}

	/** WS-02 Workspace 상세 */
	@GetMapping("/{workspaceId}")
	public WorkspaceResponse getWorkspace(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return workspaceService.getWorkspace(workspaceId, userId);
	}

	/** WS-03 Workspace 수정 (호스트) */
	@PatchMapping("/{workspaceId}")
	public WorkspaceResponse update(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody UpdateWorkspaceRequest request
	) {
		return workspaceService.update(workspaceId, userId, request);
	}

	/** WS-04 Workspace 삭제 (호스트) */
	@DeleteMapping("/{workspaceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		workspaceService.delete(workspaceId, userId);
	}
}
