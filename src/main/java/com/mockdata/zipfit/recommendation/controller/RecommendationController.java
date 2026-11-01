package com.mockdata.zipfit.recommendation.controller;

import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.recommendation.dto.AnalysisResponse;
import com.mockdata.zipfit.recommendation.dto.RecommendationsResponse;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	/** REC-01 AI 분석 요청. 접수만 하고 202를 돌려준다. 결과는 「분석 진행 통지」 뒤 조회한다. */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public AnalysisResponse request(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return recommendationService.request(workspaceId, userId);
	}

	/** REC-02 가장 최근 분석의 상태와 추천 위치 */
	@GetMapping
	public RecommendationsResponse getLatest(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return recommendationService.getLatest(workspaceId, userId);
	}
}
