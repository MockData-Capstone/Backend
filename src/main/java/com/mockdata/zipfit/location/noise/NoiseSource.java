package com.mockdata.zipfit.location.noise;

/** 소음을 일으킬 수 있는 장소의 종류 */
public enum NoiseSource {
	AIRPORT("공항"),
	RAILWAY_STATION("기차역"),
	SUBWAY_STATION("지하철역"),
	BUS_TERMINAL("버스터미널"),
	BAR_CLUSTER("술집"),
	SCHOOL("학교"),
	LARGE_MART("대형마트"),
	EMERGENCY_ROOM("응급실"),
	FIRE_STATION("소방서");

	private final String label;

	NoiseSource(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
