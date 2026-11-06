package com.mockdata.zipfit.ai.dto;

/** 경사 등급 (PRD 4장): 완만 5% 이하, 보통 5% 초과~8.3% 이하, 가파름 8.3% 초과 */
public enum SlopeGrade {
	GENTLE, MODERATE, STEEP;

	public static SlopeGrade of(double maxPercent) {
		if (maxPercent <= 5.0) {
			return GENTLE;
		}
		return maxPercent <= 8.3 ? MODERATE : STEEP;
	}
}
