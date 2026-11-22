package com.mockdata.zipfit.candidate.dto;

import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.candidate.entity.LocationInfo;
import com.mockdata.zipfit.location.noise.NoiseGrade;
import com.mockdata.zipfit.location.noise.NoiseWarning;
import com.mockdata.zipfit.location.sunlight.SunlightGrade;
import java.util.List;

/**
 * 입지 정보 (LOC-03). 산출하지 못한 항목은 null이며 "정보 없음"으로 표시한다.
 * estimated가 true면 "추정치 — 현장 확인 필요"로 표시한다 (FR-023). 일조·소음(LOC-04)은 항상 추정치다.
 */
public record LocationResponse(Slope slope, Vehicle vehicleAccess, Sunlight sunlight, Noise noise) {

	static final String SUNLIGHT_SOURCE = "브이월드 GIS건물통합정보 + 동지 태양 위치 계산";
	static final String NOISE_SOURCE = "카카오 로컬 장소 검색 (주변 소음원 거리)";

	public static LocationResponse of(LocationInfo info) {
		if (info == null) {
			return new LocationResponse(null, null, null, null);
		}
		Slope slope = info.getSlopeGrade() == null ? null : new Slope(
			info.getSlopeAveragePercent(), info.getSlopeMaxPercent(), info.getSlopeGrade(), info.getSlopeDestination(),
			info.getSlopeSource(), info.getSlopeConfidence() != Confidence.HIGH
		);
		Vehicle vehicle = info.getVehicleAccess() == null ? null : new Vehicle(
			info.getVehicleAccess(), info.getVehicleReason(), info.getVehicleSource(),
			info.getVehicleConfidence() != Confidence.HIGH
		);
		Sunlight sunlight = info.getSunlightGrade() == null ? null : Sunlight.of(
			info.getSunlightGrade(), info.getSunlightTotalMinutes(), info.getSunlightContinuousMinutes());
		Noise noise = info.getNoiseGrade() == null ? null : new Noise(
			info.getNoiseGrade(), info.getNoiseWarnings() == null ? List.of() : info.getNoiseWarnings(), NOISE_SOURCE, true);
		return new LocationResponse(slope, vehicle, sunlight, noise);
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

	/**
	 * LOC-04 일조 (동지 기준).
	 *
	 * @param totalMinutes      08~16시에 해가 드는 시간 합계(분)
	 * @param continuousMinutes 09~15시 중 가장 길게 이어서 해가 드는 시간(분)
	 * @param message           화면에 그대로 보여 줄 문장
	 */
	public record Sunlight(
		SunlightGrade grade,
		int totalMinutes,
		int continuousMinutes,
		String message,
		String source,
		boolean estimated
	) {

		static Sunlight of(SunlightGrade grade, Integer totalMinutes, Integer continuousMinutes) {
			int total = totalMinutes == null ? 0 : totalMinutes;
			int continuous = continuousMinutes == null ? 0 : continuousMinutes;
			String message = "동지 기준 하루 %s 해가 들어요 (연속 최대 %s)".formatted(duration(total), duration(continuous));
			return new Sunlight(grade, total, continuous, message, SUNLIGHT_SOURCE, true);
		}

		private static String duration(int minutes) {
			if (minutes == 0) {
				return "0분";
			}
			int hours = minutes / 60;
			int rest = minutes % 60;
			if (hours == 0) {
				return rest + "분";
			}
			return rest == 0 ? hours + "시간" : hours + "시간 " + rest + "분";
		}
	}

	/** LOC-04 소음. warnings가 비어 있으면 주변에 확인된 소음원이 없다는 뜻이다 (grade = LOW). */
	public record Noise(NoiseGrade grade, List<NoiseWarning> warnings, String source, boolean estimated) {
	}
}
