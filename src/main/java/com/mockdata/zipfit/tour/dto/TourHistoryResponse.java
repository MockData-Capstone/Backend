package com.mockdata.zipfit.tour.dto;

import com.mockdata.zipfit.tour.entity.TourEnums.EndReason;
import java.time.Instant;

/** TOUR-06 종료된 투어. 영상은 저장하지 않으므로 시각 정보만 있다. */
public record TourHistoryResponse(
	Long tourId,
	TourResponse.Visitor visitor,
	Instant startedAt,
	Instant endedAt,
	EndReason endReason
) {
}
