package com.mockdata.zipfit.comparison.controller;

import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.comparison.dto.ComparisonResponse;
import com.mockdata.zipfit.comparison.service.ComparisonService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CMP-01 비교표. 기능명세서에 따라 저장하지 않으므로 GET과 POST 모두 요청할 때 최신 정보로 만들어 돌려준다.
 * candidateIds를 주면 그 후보지만 비교한다 (예: ?candidateIds=1,2).
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/comparisons")
@RequiredArgsConstructor
public class ComparisonController {

	private final ComparisonService comparisonService;

	@GetMapping
	public ComparisonResponse get(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@RequestParam(defaultValue = "") Set<Long> candidateIds
	) {
		return comparisonService.compare(workspaceId, userId, candidateIds);
	}

	/** API명세서의 「비교표 생성 및 갱신」. 저장하지 않으므로 GET과 같은 결과다. */
	@PostMapping
	public ComparisonResponse generate(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@RequestParam(defaultValue = "") Set<Long> candidateIds
	) {
		return comparisonService.compare(workspaceId, userId, candidateIds);
	}
}
