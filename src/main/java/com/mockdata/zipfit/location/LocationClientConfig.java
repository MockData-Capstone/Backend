package com.mockdata.zipfit.location;

import com.mockdata.zipfit.location.building.BuildingClient;
import com.mockdata.zipfit.location.building.VWorldBuildingClient;
import com.mockdata.zipfit.location.place.KakaoPlaceSearchClient;
import com.mockdata.zipfit.location.place.PlaceSearchClient;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 일조·소음 추정(LOC-04)에 쓰는 외부 API. 키가 없으면 해당 항목은 "정보 없음"이 된다. */
@Slf4j
@Configuration
public class LocationClientConfig {

	@Bean
	public PlaceSearchClient placeSearchClient(
		@Value("${location.kakao.rest-api-key:}") String restApiKey,
		@Value("${location.connect-timeout:3s}") Duration connectTimeout,
		@Value("${location.read-timeout:10s}") Duration readTimeout
	) {
		if (restApiKey.isBlank()) {
			log.warn("KAKAO_REST_API_KEY가 없어 소음 추정을 하지 않습니다 (정보 없음).");
			return Unavailable.PLACES;
		}
		return new KakaoPlaceSearchClient(restApiKey, connectTimeout, readTimeout);
	}

	@Bean
	public BuildingClient buildingClient(
		@Value("${location.vworld.api-key:}") String apiKey,
		@Value("${location.vworld.domain:}") String domain,
		@Value("${location.connect-timeout:3s}") Duration connectTimeout,
		@Value("${location.read-timeout:10s}") Duration readTimeout
	) {
		if (apiKey.isBlank() || domain.isBlank()) {
			log.warn("VWORLD_API_KEY 또는 VWORLD_DOMAIN이 없어 일조 추정을 하지 않습니다 (정보 없음).");
			return Unavailable.BUILDINGS;
		}
		return new VWorldBuildingClient(apiKey, domain, connectTimeout, readTimeout);
	}

	/** 키가 없을 때 쓰는 구현. available()이 false라 추정기가 부르지 않는다. */
	private static final class Unavailable {

		static final PlaceSearchClient PLACES = new PlaceSearchClient() {
			@Override
			public boolean available() {
				return false;
			}

			@Override
			public List<Place> searchCategory(String code, double latitude, double longitude, int radius) {
				return List.of();
			}

			@Override
			public List<Place> searchKeyword(String query, double latitude, double longitude, int radius) {
				return List.of();
			}
		};

		static final BuildingClient BUILDINGS = new BuildingClient() {
			@Override
			public boolean available() {
				return false;
			}

			@Override
			public List<Building> findBuildings(double latitude, double longitude, int radiusMeters) {
				return List.of();
			}
		};
	}
}
