package com.mockdata.zipfit.condition.dto;

import com.mockdata.zipfit.ai.dto.RoomType;
import com.mockdata.zipfit.condition.entity.HouseCondition;
import com.mockdata.zipfit.workspace.dto.MemberProfile;
import java.time.Instant;

/**
 * 확정 조건 (COND-02). CONDITION_UPDATED의 data로도 쓴다.
 * 다른 멤버가 덮어쓰면 lastModifiedBy로 누가 바꿨는지 표시한다 (제안).
 */
public record ConditionResponse(
	Integer depositMin,
	Integer depositMax,
	Integer monthlyRentMin,
	Integer monthlyRentMax,
	RoomType roomType,
	Double areaMin,
	Double areaMax,
	Boolean avoidSlope,
	Boolean needVehicleAccess,
	MemberProfile lastModifiedBy,
	Instant updatedAt
) {

	public static ConditionResponse of(HouseCondition c) {
		return new ConditionResponse(
			c.getDepositMin(), c.getDepositMax(), c.getMonthlyRentMin(), c.getMonthlyRentMax(), c.getRoomType(),
			c.getAreaMin(), c.getAreaMax(), c.getAvoidSlope(), c.getNeedVehicleAccess(),
			new MemberProfile(c.getLastModifiedByUserId(), c.getLastModifiedByNickname(), null),
			c.getUpdatedAt()
		);
	}
}
