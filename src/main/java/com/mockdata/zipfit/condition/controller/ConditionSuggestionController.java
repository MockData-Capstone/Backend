package com.mockdata.zipfit.condition.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.condition.dto.SuggestionResponse;
import com.mockdata.zipfit.condition.entity.SuggestionType;
import com.mockdata.zipfit.condition.service.ConditionSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REC-03 조건 제안. API명세서에 행이 없어 기능명세서를 보고 경로를 정했다. */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/condition-suggestions")
@RequiredArgsConstructor
public class ConditionSuggestionController {

	private final ConditionSuggestionService suggestionService;

	/** 근거가 있는 조건 제안 목록 (없으면 빈 목록) */
	@GetMapping
	public ListResponse<SuggestionResponse> getSuggestions(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(suggestionService.getSuggestions(workspaceId, userId));
	}

	/** 제안을 받아들여 조건 확정 초기값(이미 확정했으면 확정 조건)에 넣는다. 바뀐 제안 목록을 돌려준다. */
	@PostMapping("/{type}/accept")
	public ListResponse<SuggestionResponse> accept(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@PathVariable SuggestionType type
	) {
		return new ListResponse<>(suggestionService.accept(workspaceId, userId, type));
	}
}
