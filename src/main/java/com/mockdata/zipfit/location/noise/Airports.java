package com.mockdata.zipfit.location.noise;

import java.util.List;

/**
 * 전국 공항 (여객터미널 좌표, 카카오맵 2026-11 확인). 대구·광주·청주·사천·원주·군산·김해 등 군 공항을 함께 쓰는 곳도 들어 있다.
 * 군 전용 비행장(수원·서산·오산·성남 등)은 지도 서비스에 위치가 나오지 않아 넣지 않았다. 확인한 좌표가 생기면 여기에 더한다.
 */
final class Airports {

	record Airport(String name, double latitude, double longitude) {
	}

	static final List<Airport> ALL = List.of(
		new Airport("인천국제공항", 37.449168, 126.450920),
		new Airport("김포국제공항", 37.558677, 126.802781),
		new Airport("김해국제공항", 35.172488, 128.946785),
		new Airport("제주국제공항", 33.506840, 126.492723),
		new Airport("대구국제공항", 35.899841, 128.637838),
		new Airport("청주국제공항", 36.722027, 127.495856),
		new Airport("광주공항", 35.139934, 126.810712),
		new Airport("무안국제공항", 34.993792, 126.387714),
		new Airport("양양국제공항", 38.058771, 128.662894),
		new Airport("여수공항", 34.840208, 127.613959),
		new Airport("울산공항", 35.592851, 129.355897),
		new Airport("포항경주공항", 35.984113, 129.434039),
		new Airport("사천공항", 35.092246, 128.086608),
		new Airport("원주공항", 37.459212, 127.977121),
		new Airport("군산공항", 35.926056, 126.615496)
	);

	private Airports() {
	}
}
