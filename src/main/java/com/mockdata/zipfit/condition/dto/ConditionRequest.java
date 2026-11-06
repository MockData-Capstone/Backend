package com.mockdata.zipfit.condition.dto;

import com.mockdata.zipfit.ai.dto.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * COND-01·03 조건 확정·수정. 화면의 값 전체를 보낸다(빈 항목은 null). 금액은 만원, 면적은 ㎡다.
 * 방 구조와 ㎡ 범위는 둘 다 입력할 수 있다.
 */
public record ConditionRequest(
	@PositiveOrZero @Max(1_000_000) Integer depositMin,
	@PositiveOrZero @Max(1_000_000) Integer depositMax,
	@PositiveOrZero @Max(10_000) Integer monthlyRentMin,
	@PositiveOrZero @Max(10_000) Integer monthlyRentMax,
	RoomType roomType,
	@PositiveOrZero @Max(1_000) Double areaMin,
	@PositiveOrZero @Max(1_000) Double areaMax,
	Boolean avoidSlope,
	Boolean needVehicleAccess
) {
}
