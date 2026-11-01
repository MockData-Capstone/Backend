package com.mockdata.zipfit.ai.dto;

/**
 * POST /api/ai/slope 응답. 목적지가 여러 곳이면 가장 가파른 경로를 대표로 쓴다.
 *
 * @param averagePercent 경로를 구하지 못했으면 null ("경로 정보 없음")
 * @param destination    산출에 쓴 목적지 (예: "성수역")
 * @param source         데이터 출처 (예: "수치지형도", "DEM")
 */
public record SlopeResponse(
	Double averagePercent,
	Double maxPercent,
	SlopeGrade grade,
	String destination,
	String source,
	Confidence confidence
) {
}
