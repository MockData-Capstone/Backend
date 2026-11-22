package com.mockdata.zipfit.location.sunlight;

/**
 * 동지 기준 일조 추정 결과.
 *
 * @param totalMinutes       08~16시에 해가 드는 시간 합계(분)
 * @param continuousMinutes  09~15시 중 가장 길게 이어서 해가 드는 시간(분)
 */
public record SunlightResult(SunlightGrade grade, int totalMinutes, int continuousMinutes) {

	public static SunlightResult of(int totalMinutes, int continuousMinutes) {
		return new SunlightResult(SunlightGrade.of(totalMinutes, continuousMinutes), totalMinutes, continuousMinutes);
	}
}
