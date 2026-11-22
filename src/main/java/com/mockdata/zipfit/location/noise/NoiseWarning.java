package com.mockdata.zipfit.location.noise;

/**
 * 후보지 주변의 소음 주의 항목 하나.
 *
 * @param source          소음원 종류
 * @param level           이 항목의 위험 정도 (MEDIUM 유의, HIGH 높음)
 * @param name            가장 가까운 장소 이름. 개수로 판단한 항목(술집)은 null
 * @param distanceMeters  가장 가까운 장소까지 거리(m). 개수로 판단한 항목은 null
 * @param count           반경 안의 개수. 개수로 판단한 항목만 값이 있다
 * @param message         화면에 그대로 보여 줄 문장
 */
public record NoiseWarning(
	NoiseSource source,
	NoiseGrade level,
	String name,
	Integer distanceMeters,
	Integer count,
	String message
) {
}
