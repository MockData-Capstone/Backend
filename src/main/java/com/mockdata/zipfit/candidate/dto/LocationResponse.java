package com.mockdata.zipfit.candidate.dto;

import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.candidate.entity.LocationInfo;

/**
 * 입지 정보 (LOC-03). 산출하지 못한 항목은 null이며 "정보 없음"으로 표시한다.
 * estimated가 true면 "추정치 — 현장 확인 필요"로 표시한다 (FR-023).
 */
public record LocationResponse(Slope slope, Vehicle vehicleAccess) {

	public static LocationResponse of(LocationInfo info) {
		if (info == null) {
			return new LocationResponse(null, null);
		}
		Slope slope = info.getSlopeGrade() == null ? null : new Slope(
			info.getSlopeAveragePercent(), info.getSlopeMaxPercent(), info.getSlopeGrade(), info.getSlopeDestination(),
			info.getSlopeSource(), info.getSlopeConfidence() != Confidence.HIGH
		);
		Vehicle vehicle = info.getVehicleAccess() == null ? null : new Vehicle(
			info.getVehicleAccess(), info.getVehicleReason(), info.getVehicleSource(),
			info.getVehicleConfidence() != Confidence.HIGH
		);
		return new LocationResponse(slope, vehicle);
	}

	/** @param destination 경사를 잰 목적지 (학교·직장, 없으면 가장 가까운 지하철역) */
	public record Slope(
		Double averagePercent,
		Double maxPercent,
		SlopeGrade grade,
		String destination,
		String source,
		boolean estimated
	) {
	}

	public record Vehicle(VehicleAccess access, String reason, String source, boolean estimated) {
	}
}
