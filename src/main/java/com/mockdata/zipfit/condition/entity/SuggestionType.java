package com.mockdata.zipfit.condition.entity;

/**
 * 조건 제안 유형 (REC-03).
 * 편의시설 제안은 후보 주변 시설 정보(지도 API)가 필요해 아직 만들지 않는다.
 */
public enum SuggestionType {
	/** 경사 조건: 오르막 회피 */
	SLOPE,
	/** 차량 조건: 차량 진입 필요 */
	VEHICLE_ACCESS
}
