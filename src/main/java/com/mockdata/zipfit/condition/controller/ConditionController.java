package com.mockdata.zipfit.condition.controller;

import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.condition.dto.ConditionRequest;
import com.mockdata.zipfit.condition.dto.ConditionResponse;
import com.mockdata.zipfit.condition.dto.ConditionView;
import com.mockdata.zipfit.condition.service.ConditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API명세서에 행이 없어 기능명세서(COND-01~03)를 보고 경로를 정했다. Workspace당 1건이라 단수형이다. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/condition")
@RequiredArgsConstructor
public class ConditionController {

	private final ConditionService conditionService;

	/** COND-02 확정 조건 조회 (없으면 condition: null, 초기값은 draft) */
	@GetMapping
	public ConditionView get(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return conditionService.get(workspaceId, userId);
	}

	/** COND-01 조건 확정 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ConditionResponse confirm(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody ConditionRequest request
	) {
		return conditionService.confirm(workspaceId, userId, request);
	}

	/** COND-03 확정 조건 수정. 화면의 값 전체로 바꾸므로 PUT이다. */
	@PutMapping
	public ConditionResponse update(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody ConditionRequest request
	) {
		return conditionService.update(workspaceId, userId, request);
	}
}
