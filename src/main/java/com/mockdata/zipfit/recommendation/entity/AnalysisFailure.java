package com.mockdata.zipfit.recommendation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 분석 실패 사유. message는 화면에 그대로 보여줄 안내다. */
@Getter
@RequiredArgsConstructor
public enum AnalysisFailure {
	NO_CONDITIONS("대화에서 집 조건을 찾지 못했습니다. 지역, 예산, 방 구조 등을 더 이야기해주세요."),
	AI_ERROR("AI 분석에 실패했습니다. 다시 요청해주세요."),
	TIMEOUT("분석 시간이 초과되었습니다. 다시 요청해주세요.");

	private final String message;
}
