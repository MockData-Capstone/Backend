package com.mockdata.zipfit.location.noise;

import java.util.List;

/** 소음 추정 결과. 등급은 주의 항목 중 가장 높은 단계이고, 항목이 없으면 LOW다. */
public record NoiseResult(NoiseGrade grade, List<NoiseWarning> warnings) {

	public static NoiseResult of(List<NoiseWarning> warnings) {
		NoiseGrade grade = NoiseGrade.LOW;
		for (NoiseWarning warning : warnings) {
			grade = grade.max(warning.level());
		}
		return new NoiseResult(grade, List.copyOf(warnings));
	}
}
