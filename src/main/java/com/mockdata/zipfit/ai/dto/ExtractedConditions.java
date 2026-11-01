package com.mockdata.zipfit.ai.dto;

import java.util.List;

/**
 * 대화에서 추출한 집 조건 8가지 (FR-011). 대화에 없던 조건은 null(목록은 빈 배열)이다.
 * 조건 확정(COND-01) 화면의 초기값이 된다. 금액 단위는 만원, 면적 단위는 ㎡다.
 *
 * @param region            지역 (예: "성수동")
 * @param roomType          방 구조
 * @param facilities        편의시설 (예: "편의점", "헬스장")
 * @param destinations      목적지 (예: "한양대학교")
 * @param avoidSlope        경사 조건: 오르막 회피
 * @param needVehicleAccess 차량 조건: 차량 진입 필요
 */
public record ExtractedConditions(
	String region,
	Integer depositMin,
	Integer depositMax,
	Integer monthlyRentMin,
	Integer monthlyRentMax,
	RoomType roomType,
	Double areaMin,
	Double areaMax,
	List<String> facilities,
	List<String> destinations,
	Boolean avoidSlope,
	Boolean needVehicleAccess
) {

	public ExtractedConditions {
		facilities = facilities == null ? List.of() : List.copyOf(facilities);
		destinations = destinations == null ? List.of() : List.copyOf(destinations);
	}

	/** 조건을 하나도 얻지 못했는지. 그러면 대화 보충을 안내한다. */
	public boolean isEmpty() {
		return region == null && depositMin == null && depositMax == null && monthlyRentMin == null
			&& monthlyRentMax == null && roomType == null && areaMin == null && areaMax == null
			&& facilities.isEmpty() && destinations.isEmpty() && avoidSlope == null && needVehicleAccess == null;
	}
}
