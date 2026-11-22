package com.mockdata.zipfit.location.sunlight;

import java.time.Instant;

/**
 * 태양 위치 (고도·방위각). 천문 연감의 저정밀 공식이며 1800~2200년에서 오차가 약 0.01°다.
 *
 * @param altitudeDegrees 지평선 위 고도 (음수면 해가 진 것)
 * @param azimuthDegrees  북쪽에서 시계 방향으로 잰 방위각 (0 북, 90 동, 180 남, 270 서)
 */
public record SolarPosition(double altitudeDegrees, double azimuthDegrees) {

	public static SolarPosition at(Instant instant, double latitude, double longitude) {
		double jd = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5;
		double n = jd - 2_451_545.0;

		double meanLongitude = normalizeDegrees(280.460 + 0.9856474 * n);
		double meanAnomaly = Math.toRadians(normalizeDegrees(357.528 + 0.9856003 * n));
		double eclipticLongitude = Math.toRadians(meanLongitude
			+ 1.915 * Math.sin(meanAnomaly) + 0.020 * Math.sin(2 * meanAnomaly));
		double obliquity = Math.toRadians(23.439 - 0.0000004 * n);

		double rightAscension = Math.atan2(
			Math.cos(obliquity) * Math.sin(eclipticLongitude), Math.cos(eclipticLongitude));
		double declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLongitude));

		double gmstHours = normalize(18.697374558 + 24.06570982441908 * n, 24);
		double localSiderealDegrees = gmstHours * 15 + longitude;
		double hourAngle = Math.toRadians(localSiderealDegrees) - rightAscension;

		double lat = Math.toRadians(latitude);
		double altitude = Math.asin(Math.sin(lat) * Math.sin(declination)
			+ Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle));
		double azimuth = Math.atan2(
			-Math.cos(declination) * Math.sin(hourAngle),
			Math.sin(declination) * Math.cos(lat) - Math.cos(declination) * Math.sin(lat) * Math.cos(hourAngle));

		return new SolarPosition(Math.toDegrees(altitude), normalizeDegrees(Math.toDegrees(azimuth)));
	}

	private static double normalizeDegrees(double degrees) {
		return normalize(degrees, 360);
	}

	private static double normalize(double value, double range) {
		double result = value % range;
		return result < 0 ? result + range : result;
	}
}
