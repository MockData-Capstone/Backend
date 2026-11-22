package com.mockdata.zipfit.location.sunlight;

import com.mockdata.zipfit.location.building.BuildingClient;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LOC-04 일조 추정. 브이월드 GIS건물통합정보(건물 윤곽·높이)와 동지 태양 위치로 하루 일조 시간을 계산한다.
 * 동지(12월 22일) 정오 태양 고도는 서울 약 29°, 제주 약 33°라 그림자가 길다. 9~15시 그림자는 건물 높이의 3~4배까지
 * 뻗으므로 반경 200m 안의 건물을 본다. 그보다 먼 고층 건물이 아침·저녁 해를 가리는 경우는 빠질 수 있다.
 */
@Component
@RequiredArgsConstructor
public class SunlightEstimator {

	static final int BUILDING_RADIUS_METERS = 200;

	private final BuildingClient buildingClient;

	/** 키가 없어 조회할 수 없으면 비어 있다("정보 없음"). */
	public Optional<SunlightResult> estimate(double latitude, double longitude) {
		if (!buildingClient.available()) {
			return Optional.empty();
		}
		LocalDate winterSolstice = LocalDate.of(LocalDate.now(SunlightCalculator.KOREA).getYear(), 12, 22);
		return Optional.of(SunlightCalculator.calculate(latitude, longitude,
			buildingClient.findBuildings(latitude, longitude, BUILDING_RADIUS_METERS), winterSolstice));
	}
}
