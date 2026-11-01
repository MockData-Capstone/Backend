package com.mockdata.zipfit.ai.dto;

/**
 * POST /api/ai/vehicle-access 응답 (LOC-02)
 *
 * @param reason 판정 근거 (예: "도로 폭 약 3m, 양쪽 주차로 통행 폭 좁음")
 * @param source 데이터 출처 (예: "도로명주소 도로구간", "팀 촬영 이미지")
 */
public record VehicleAccessResponse(VehicleAccess access, String reason, String source, Confidence confidence) {
}
