package com.mockdata.zipfit.location.building;

import java.util.List;

/** 좌표 주변 건물의 윤곽과 높이 (일조 추정, LOC-04). 구현: 브이월드 GIS건물통합정보 */
public interface BuildingClient {

	/** 키가 없어 조회할 수 없으면 false. 이때 일조는 "정보 없음"이다. */
	boolean available();

	/** 중심에서 동서남북으로 radiusMeters 안(사각형)에 걸친 건물 */
	List<Building> findBuildings(double latitude, double longitude, int radiusMeters);

	/**
	 * @param outline       건물 바깥 윤곽. 점마다 {경도, 위도} (EPSG:4326). 여러 동으로 나뉜 건물은 Building을 여러 개로 나눈다.
	 * @param heightMeters  높이 (자료에 없으면 층수로 추정한 값)
	 */
	record Building(List<double[]> outline, double heightMeters) {
	}
}
