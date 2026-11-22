package com.mockdata.zipfit.location.sunlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mockdata.zipfit.location.building.BuildingClient.Building;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LOC-04 일조 계산 (외부 API 없이) */
class SunlightCalculatorTest {

	private static final double SEOUL_LAT = 37.5665;
	private static final double SEOUL_LNG = 126.9780;
	private static final LocalDate SOLSTICE = LocalDate.of(2026, 12, 22);

	@Test
	@DisplayName("동지 서울의 태양: 정오 무렵 남쪽에 고도 약 29°, 오전에는 남동쪽에 낮게 뜬다")
	void solarPosition() {
		// 서울의 태양 남중은 경도 차이로 12:30 무렵이다. 남중 고도 = 90 - 37.57 - 23.44 ≈ 29.0°
		SolarPosition noon = SolarPosition.at(at(12, 32), SEOUL_LAT, SEOUL_LNG);
		assertThat(noon.altitudeDegrees()).isCloseTo(29.0, within(0.5));
		assertThat(noon.azimuthDegrees()).isCloseTo(180.0, within(2.0));

		SolarPosition morning = SolarPosition.at(at(9, 0), SEOUL_LAT, SEOUL_LNG);
		assertThat(morning.altitudeDegrees()).isBetween(5.0, 15.0);
		assertThat(morning.azimuthDegrees()).isBetween(120.0, 150.0);

		assertThat(SolarPosition.at(at(6, 0), SEOUL_LAT, SEOUL_LNG).altitudeDegrees()).isNegative();
	}

	@Test
	@DisplayName("가리는 건물이 없으면 08~16시 내내 해가 들어 '충분'이다")
	void openSky() {
		SunlightResult result = SunlightCalculator.calculate(SEOUL_LAT, SEOUL_LNG, List.of(), SOLSTICE);

		assertThat(result.totalMinutes()).isEqualTo(480);
		assertThat(result.continuousMinutes()).isEqualTo(360);
		assertThat(result.grade()).isEqualTo(SunlightGrade.SUFFICIENT);
	}

	@Test
	@DisplayName("바로 남쪽의 높은 건물이 해를 가리면 '부족'이고, 북쪽 건물이나 자기 건물은 가리지 않는다")
	void blockedBySouthBuilding() {
		Building southTower = rectangle(-100, -15, 100, -5, 60);
		assertThat(SunlightCalculator.calculate(SEOUL_LAT, SEOUL_LNG, List.of(southTower), SOLSTICE).grade())
			.isEqualTo(SunlightGrade.INSUFFICIENT);

		Building northTower = rectangle(-100, 5, 100, 15, 60);
		Building ownBuilding = rectangle(-10, -10, 10, 10, 60);
		assertThat(SunlightCalculator.calculate(SEOUL_LAT, SEOUL_LNG, List.of(northTower, ownBuilding), SOLSTICE)
			.totalMinutes()).isEqualTo(480);
	}

	@Test
	@DisplayName("낮은 건물은 가까워도 그림자가 짧아 정오 무렵 해를 가리지 않는다")
	void lowBuildingShortShadow() {
		// 남쪽 10m, 높이 6m: 고도 tan⁻¹(4.5/10) ≈ 24° 아래일 때만 가린다 → 정오 전후만 해가 든다
		Building low = rectangle(-100, -12, 100, -10, 6);
		SunlightResult result = SunlightCalculator.calculate(SEOUL_LAT, SEOUL_LNG, List.of(low), SOLSTICE);

		assertThat(result.totalMinutes()).isBetween(60, 300);
		assertThat(result.totalMinutes()).isLessThan(480);
	}

	@Test
	@DisplayName("판례 수인한도: 연속 2시간·총 4시간 둘 다면 충분, 하나면 보통, 없으면 부족")
	void grade() {
		assertThat(SunlightGrade.of(240, 120)).isEqualTo(SunlightGrade.SUFFICIENT);
		assertThat(SunlightGrade.of(240, 110)).isEqualTo(SunlightGrade.MODERATE);
		assertThat(SunlightGrade.of(200, 120)).isEqualTo(SunlightGrade.MODERATE);
		assertThat(SunlightGrade.of(200, 110)).isEqualTo(SunlightGrade.INSUFFICIENT);
	}

	private static java.time.Instant at(int hour, int minute) {
		return LocalDateTime.of(SOLSTICE.getYear(), 12, 22, hour, minute).atZone(SunlightCalculator.KOREA).toInstant();
	}

	/** 기준점(서울)에서 동쪽 x, 북쪽 y(m)로 지정한 사각형 건물 */
	private static Building rectangle(double x1, double y1, double x2, double y2, double height) {
		double dLat = 1 / 110_574.0;
		double dLng = 1 / (111_320.0 * Math.cos(Math.toRadians(SEOUL_LAT)));
		return new Building(List.of(
			new double[] {SEOUL_LNG + x1 * dLng, SEOUL_LAT + y1 * dLat},
			new double[] {SEOUL_LNG + x2 * dLng, SEOUL_LAT + y1 * dLat},
			new double[] {SEOUL_LNG + x2 * dLng, SEOUL_LAT + y2 * dLat},
			new double[] {SEOUL_LNG + x1 * dLng, SEOUL_LAT + y2 * dLat}
		), height);
	}
}
