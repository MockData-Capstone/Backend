package com.mockdata.zipfit.location.sunlight;

import com.mockdata.zipfit.location.building.BuildingClient.Building;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 주변 건물이 태양을 가리는지로 하루 일조 시간을 계산한다.
 * 10분마다(각 구간의 가운데 시각) 후보지에서 태양 방향으로 선을 긋고, 그 선이 지나는 건물의 높이가
 * "거리 × tan(태양 고도)"보다 높으면 그늘로 본다. 후보지가 들어 있는 건물(자기 건물)은 뺀다.
 * 층과 창문 방향은 알 수 없어 1층 창 높이(1.5m)에서 방향과 관계없이 계산한다. 산·언덕은 반영하지 않는다.
 */
public final class SunlightCalculator {

	static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	static final double OBSERVER_HEIGHT_METERS = 1.5;
	static final int STEP_MINUTES = 10;
	private static final int DAY_START = 8 * 60;
	private static final int DAY_END = 16 * 60;
	private static final int CONTINUOUS_START = 9 * 60;
	private static final int CONTINUOUS_END = 15 * 60;
	private static final double METERS_PER_DEGREE_LAT = 110_574.0;
	private static final double METERS_PER_DEGREE_LNG_AT_EQUATOR = 111_320.0;

	private SunlightCalculator() {
	}

	public static SunlightResult calculate(double latitude, double longitude, List<Building> buildings, LocalDate date) {
		List<LocalBuilding> obstacles = toLocal(latitude, longitude, buildings);

		int total = 0;
		int run = 0;
		int longestRun = 0;
		for (int minute = DAY_START + STEP_MINUTES / 2; minute < DAY_END; minute += STEP_MINUTES) {
			SolarPosition sun = SolarPosition.at(
				date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(KOREA).toInstant(), latitude, longitude);
			boolean sunlit = sun.altitudeDegrees() > 0 && !blocked(obstacles, sun);
			if (sunlit) {
				total += STEP_MINUTES;
			}
			if (minute >= CONTINUOUS_START && minute < CONTINUOUS_END) {
				run = sunlit ? run + STEP_MINUTES : 0;
				longestRun = Math.max(longestRun, run);
			}
		}
		return SunlightResult.of(total, longestRun);
	}

	private static boolean blocked(List<LocalBuilding> obstacles, SolarPosition sun) {
		double azimuth = Math.toRadians(sun.azimuthDegrees());
		double dx = Math.sin(azimuth);
		double dy = Math.cos(azimuth);
		double tanAltitude = Math.tan(Math.toRadians(sun.altitudeDegrees()));
		for (LocalBuilding building : obstacles) {
			double distance = nearestHit(building.points(), dx, dy);
			if (distance < Double.MAX_VALUE && building.height() - OBSERVER_HEIGHT_METERS > distance * tanAltitude) {
				return true;
			}
		}
		return false;
	}

	/** 원점에서 (dx, dy) 방향으로 그은 반직선이 다각형 변과 처음 만나는 거리. 만나지 않으면 Double.MAX_VALUE */
	static double nearestHit(double[][] points, double dx, double dy) {
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < points.length; i++) {
			double[] p = points[i];
			double[] q = points[(i + 1) % points.length];
			double ex = q[0] - p[0];
			double ey = q[1] - p[1];
			double denominator = dx * ey - dy * ex;
			if (Math.abs(denominator) < 1e-12) {
				continue;
			}
			double t = (p[0] * ey - p[1] * ex) / denominator;
			double s = (p[0] * dy - p[1] * dx) / denominator;
			if (t >= 0 && s >= 0 && s <= 1) {
				nearest = Math.min(nearest, t);
			}
		}
		return nearest;
	}

	/** 원점이 다각형 안에 있는지 (반직선 교차 횟수) */
	static boolean containsOrigin(double[][] points) {
		boolean inside = false;
		for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
			double yi = points[i][1];
			double yj = points[j][1];
			if ((yi > 0) != (yj > 0)) {
				double xCross = points[i][0] + (0 - yi) * (points[j][0] - points[i][0]) / (yj - yi);
				if (xCross > 0) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	/** 위경도를 후보지 기준 평면 좌표(동쪽 x, 북쪽 y, m)로 바꾸고, 후보지가 들어 있는 건물은 뺀다. */
	private static List<LocalBuilding> toLocal(double latitude, double longitude, List<Building> buildings) {
		double metersPerLng = METERS_PER_DEGREE_LNG_AT_EQUATOR * Math.cos(Math.toRadians(latitude));
		List<LocalBuilding> result = new ArrayList<>(buildings.size());
		for (Building building : buildings) {
			double[][] points = new double[building.outline().size()][];
			for (int i = 0; i < points.length; i++) {
				double[] lngLat = building.outline().get(i);
				points[i] = new double[] {
					(lngLat[0] - longitude) * metersPerLng,
					(lngLat[1] - latitude) * METERS_PER_DEGREE_LAT
				};
			}
			if (points.length >= 3 && !containsOrigin(points)) {
				result.add(new LocalBuilding(points, building.heightMeters()));
			}
		}
		return result;
	}

	private record LocalBuilding(double[][] points, double height) {
	}
}
