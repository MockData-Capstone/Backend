package com.mockdata.zipfit.location;

/** 위경도 두 점 사이의 거리 (하버사인 공식, 지구 반지름 6,371km) */
public final class GeoDistance {

	private static final double EARTH_RADIUS_METERS = 6_371_000;

	private GeoDistance() {
	}

	public static double meters(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
	}
}
