package com.mockdata.zipfit.ai;

import com.mockdata.zipfit.ai.dto.AiRecommendationRequest;
import com.mockdata.zipfit.ai.dto.AiRecommendationResponse;
import com.mockdata.zipfit.ai.dto.SlopeRequest;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccessRequest;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;

/**
 * AI 서버 내부 API (API명세서 recommendation 카테고리의 /api/ai/*).
 * 대화는 자체 서버의 로컬 LLM에서만 처리하고 외부 LLM API로 보내지 않는다 (NFR-016).
 * 실패하면 {@link AiServerException}을 던진다.
 */
public interface AiClient {

	/** POST /api/ai/recommendations: 대화에서 조건을 추출하고 추천 위치를 만든다 (REC-01). */
	AiRecommendationResponse recommend(AiRecommendationRequest request);

	/** POST /api/ai/slope: 좌표에서 목적지까지 도보 경로의 경사도 (LOC-01). */
	SlopeResponse estimateSlope(SlopeRequest request);

	/** POST /api/ai/vehicle-access: 좌표의 차량 진입성 (LOC-02). */
	VehicleAccessResponse estimateVehicleAccess(VehicleAccessRequest request);
}
