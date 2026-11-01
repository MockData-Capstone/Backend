package com.mockdata.zipfit.ai.dto;

import java.util.List;

/**
 * POST /api/ai/recommendations 응답.
 * 좌표와 장소명은 LLM이 만들지 않고 지도 데이터(카카오 로컬)에서 가져온다 (FR-012).
 */
public record AiRecommendationResponse(ExtractedConditions conditions, List<Place> places) {

	public AiRecommendationResponse {
		places = places == null ? List.of() : List.copyOf(places);
	}

	/**
	 * @param latitude    좌표를 얻지 못했으면 null (지도 핀 없이 목록에만 표시)
	 * @param kakaoPlaceId 카카오 장소 ID. 상세 시설 정보를 다시 조회할 때 쓴다.
	 */
	public record Place(String placeName, Double latitude, Double longitude, String reason, String kakaoPlaceId) {
	}
}
