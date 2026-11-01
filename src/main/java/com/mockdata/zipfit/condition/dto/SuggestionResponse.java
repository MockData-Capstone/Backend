package com.mockdata.zipfit.condition.dto;

import com.mockdata.zipfit.condition.entity.SuggestionType;
import java.util.List;

/**
 * 조건 제안 (REC-03). 근거(입지 분석 결과)와 함께 보여주고, 멤버가 받아들일 때만 조건에 넣는다.
 *
 * @param message  화면에 보여줄 제안 문구
 * @param evidence 근거가 된 후보지
 * @param accepted 이미 받아들였으면 true
 */
public record SuggestionResponse(SuggestionType type, String message, List<Evidence> evidence, boolean accepted) {

	/** @param detail 근거 설명 (예: "성수역까지 최대 경사 10.2% (가파름, 추정치)") */
	public record Evidence(Long candidateId, String candidateName, String detail) {
	}
}
