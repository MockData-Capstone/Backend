package com.mockdata.zipfit.recommendation.dto;

import java.util.List;

/**
 * REC-02 추천 위치 조회
 *
 * @param analysis 가장 최근 분석. 분석한 적이 없으면 null
 * @param items    가장 최근에 완료된 분석의 추천 위치. 재분석 중에도 이전 결과를 계속 보여준다.
 */
public record RecommendationsResponse(AnalysisResponse analysis, List<Item> items) {

	/**
	 * @param latitude    좌표를 얻지 못했으면 null (지도 핀 없이 목록에만 표시)
	 * @param candidateId 후보지로 추가했으면 그 후보지 ID, 아니면 null
	 */
	public record Item(
		Long recommendationId,
		String placeName,
		Double latitude,
		Double longitude,
		String reason,
		String kakaoPlaceId,
		boolean addedAsCandidate,
		Long candidateId
	) {
	}
}
