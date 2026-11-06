package com.mockdata.zipfit.listinglink;

import com.mockdata.zipfit.condition.entity.HouseCondition;
import java.util.ArrayList;
import java.util.List;

/** 매물 검색 링크에 넣을 수 있는 조건 */
public enum ListingCondition {
	LOCATION, DEPOSIT, MONTHLY_RENT, ROOM_TYPE, AREA;

	/** 확정 조건에 값이 있는 항목 (위치는 후보지에서 오므로 항상 있다) */
	public static List<ListingCondition> presentIn(HouseCondition c) {
		List<ListingCondition> present = new ArrayList<>(List.of(LOCATION));
		if (c.getDepositMin() != null || c.getDepositMax() != null) {
			present.add(DEPOSIT);
		}
		if (c.getMonthlyRentMin() != null || c.getMonthlyRentMax() != null) {
			present.add(MONTHLY_RENT);
		}
		if (c.getRoomType() != null) {
			present.add(ROOM_TYPE);
		}
		if (c.getAreaMin() != null || c.getAreaMax() != null) {
			present.add(AREA);
		}
		return present;
	}
}
