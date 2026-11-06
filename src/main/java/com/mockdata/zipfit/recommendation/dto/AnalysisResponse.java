package com.mockdata.zipfit.recommendation.dto;

import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.recommendation.entity.AnalysisFailure;
import com.mockdata.zipfit.recommendation.entity.AnalysisStatus;
import java.time.Instant;

/**
 * 분석 상태 (REC-01·02)
 *
 * @param failure        실패했을 때만 있다
 * @param failureMessage 화면에 보여줄 실패 안내
 * @param conditions     대화에서 추출한 조건. 분석 중이면 null
 */
public record AnalysisResponse(
	Long analysisId,
	AnalysisStatus status,
	AnalysisFailure failure,
	String failureMessage,
	ExtractedConditions conditions,
	Instant requestedAt,
	Instant completedAt
) {
}
