package com.mockdata.zipfit.candidate.entity;

import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보지의 입지 분석 결과 (LOC-01·02). 시스템이 산출하며 사용자가 수정할 수 없다.
 * 산출에 실패한 항목은 비어 있다("정보 없음").
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationInfo {

	private Double slopeAveragePercent;
	private Double slopeMaxPercent;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private SlopeGrade slopeGrade;

	@Column(length = 100)
	private String slopeDestination;

	@Column(length = 100)
	private String slopeSource;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Confidence slopeConfidence;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private VehicleAccess vehicleAccess;

	@Column(length = 300)
	private String vehicleReason;

	@Column(length = 100)
	private String vehicleSource;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Confidence vehicleConfidence;

	public static LocationInfo of(SlopeResponse slope, VehicleAccessResponse vehicle) {
		LocationInfo info = new LocationInfo();
		if (slope != null && slope.averagePercent() != null) {
			info.slopeAveragePercent = slope.averagePercent();
			info.slopeMaxPercent = slope.maxPercent();
			info.slopeGrade = slope.grade();
			info.slopeDestination = truncate(slope.destination(), 100);
			info.slopeSource = truncate(slope.source(), 100);
			info.slopeConfidence = slope.confidence();
		}
		if (vehicle != null && vehicle.access() != null) {
			info.vehicleAccess = vehicle.access();
			info.vehicleReason = truncate(vehicle.reason(), 300);
			info.vehicleSource = truncate(vehicle.source(), 100);
			info.vehicleConfidence = vehicle.confidence();
		}
		return info;
	}

	private static String truncate(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
