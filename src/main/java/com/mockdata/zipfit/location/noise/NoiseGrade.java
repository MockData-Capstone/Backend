package com.mockdata.zipfit.location.noise;

/** 소음 위험 등급 (FR-024): 낮음, 보통, 높음. 주변 소음원으로 추정한 값이며 dB 측정값이 아니다. */
public enum NoiseGrade {
	LOW, MEDIUM, HIGH;

	public NoiseGrade max(NoiseGrade other) {
		return other.ordinal() > ordinal() ? other : this;
	}
}
