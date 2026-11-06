package com.mockdata.zipfit.comparison.dto;

import com.mockdata.zipfit.candidate.dto.LocationResponse;
import com.mockdata.zipfit.candidate.dto.VoteSummary;
import java.time.Instant;
import java.util.List;

/**
 * 비교표 (CMP-01). 저장하지 않고 요청할 때 최신 후보지·입지 정보·투표로 만든다.
 * 값이 없는 항목(null)은 "정보 없음", estimated가 true인 값은 "추정치"로 표시한다.
 * 일조·소음(LOC-04, P1)은 구현되면 항목에 추가한다.
 */
public record ComparisonResponse(Instant generatedAt, List<Row> items) {

	public record Row(
		Long candidateId,
		String name,
		Double latitude,
		Double longitude,
		LocationResponse.Slope slope,
		LocationResponse.Vehicle vehicleAccess,
		VoteSummary votes
	) {
	}
}
