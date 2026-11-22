package com.mockdata.zipfit.location.place;

import java.util.List;

/** 좌표 주변 장소 검색 (소음 추정, LOC-04). 구현: 카카오 로컬 API */
public interface PlaceSearchClient {

	/** 키가 없어 검색할 수 없으면 false. 이때 소음은 "정보 없음"이다. */
	boolean available();

	/** 카테고리 그룹 코드(SW8 지하철역 등)로 반경 안의 장소를 가까운 순으로 찾는다. */
	List<Place> searchCategory(String categoryGroupCode, double latitude, double longitude, int radiusMeters);

	/** 키워드로 반경 안의 장소를 가까운 순으로 찾는다. 이름에 키워드가 든 엉뚱한 곳도 섞이므로 카테고리로 다시 거른다. */
	List<Place> searchKeyword(String query, double latitude, double longitude, int radiusMeters);

	/** @param categoryName 예: "교통,수송 > 지하철,전철 > 대구1호선" */
	record Place(String name, String categoryName, int distanceMeters) {
	}
}
