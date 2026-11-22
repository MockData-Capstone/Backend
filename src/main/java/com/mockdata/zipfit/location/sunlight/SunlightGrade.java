package com.mockdata.zipfit.location.sunlight;

/**
 * 일조 등급 (FR-024, PRD 부록). 대법원 2006다35865 일조 수인한도를 따른다:
 * 동지 09~15시 중 연속 2시간 이상, 08~16시 중 총 4시간 이상.
 * 둘 다 충족하면 충분, 하나만 충족하면 보통, 둘 다 못 하면 부족.
 */
public enum SunlightGrade {
	SUFFICIENT, MODERATE, INSUFFICIENT;

	static final int CONTINUOUS_MINUTES = 120;
	static final int TOTAL_MINUTES = 240;

	public static SunlightGrade of(int totalMinutes, int continuousMinutes) {
		boolean continuous = continuousMinutes >= CONTINUOUS_MINUTES;
		boolean total = totalMinutes >= TOTAL_MINUTES;
		if (continuous && total) {
			return SUFFICIENT;
		}
		return continuous || total ? MODERATE : INSUFFICIENT;
	}
}
