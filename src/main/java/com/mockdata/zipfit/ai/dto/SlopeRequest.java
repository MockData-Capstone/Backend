package com.mockdata.zipfit.ai.dto;

import java.util.List;

/** POST /api/ai/slope 요청. destinations가 비면 가장 가까운 지하철역까지 산출한다 (LOC-01). */
public record SlopeRequest(double latitude, double longitude, List<String> destinations) {
}
