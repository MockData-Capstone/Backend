package com.mockdata.zipfit.location.noise;

import com.mockdata.zipfit.location.GeoDistance;
import com.mockdata.zipfit.location.place.PlaceSearchClient;
import com.mockdata.zipfit.location.place.PlaceSearchClient.Place;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * LOC-04 소음 위험 추정. 공개된 소음 측정 API가 없어, 후보지 주변 소음원(역·학교·술집·공항 등)까지의 거리와 개수로 판단한다.
 * 기준 거리는 임시값이며 현장 확인 결과로 조정한다. 검색이 하나라도 실패하면 예외를 던져 "정보 없음"으로 둔다
 * (일부만 검색하고 '낮음'으로 보여 주면 잘못된 안심을 주기 때문이다).
 */
@Component
@RequiredArgsConstructor
public class NoiseEstimator {

	/** 카카오 카테고리 그룹 코드 */
	private static final String SUBWAY = "SW8";
	private static final String SCHOOL = "SC4";
	private static final String LARGE_MART = "MT1";

	static final int AIRPORT_HIGH_WITHIN = 3_000;
	/** 여객터미널 좌표 기준이라 활주로 방향에 따라 실제 영향 범위와 다를 수 있다. */
	static final int AIRPORT_MEDIUM_WITHIN = 5_000;
	static final int BAR_RADIUS = 200;
	static final int BAR_MEDIUM_COUNT = 5;
	static final int BAR_HIGH_COUNT = 15;

	/** 가장 가까운 한 곳까지의 거리로 판단하는 소음원 */
	private static final List<NearestRule> NEAREST_RULES = List.of(
		new NearestRule(NoiseSource.RAILWAY_STATION, false, "기차역", 500, 300,
			category -> category.contains("기차역"), "열차 소음에 유의하세요"),
		new NearestRule(NoiseSource.SUBWAY_STATION, true, SUBWAY, 200, 0,
			category -> true, "역 주변 유동 인구 소음이 있을 수 있어요"),
		new NearestRule(NoiseSource.BUS_TERMINAL, false, "버스터미널", 300, 0,
			category -> category.contains("버스터미널"), "버스 운행 소음이 있을 수 있어요"),
		new NearestRule(NoiseSource.SCHOOL, true, SCHOOL, 150, 0,
			category -> category.contains("초등학교") || category.contains("중학교") || category.contains("고등학교"),
			"등하교·운동장 소음이 있을 수 있어요"),
		new NearestRule(NoiseSource.LARGE_MART, true, LARGE_MART, 200, 0,
			category -> true, "차량 출입·하역 소음이 있을 수 있어요"),
		new NearestRule(NoiseSource.EMERGENCY_ROOM, false, "응급실", 300, 0,
			category -> category.contains("응급실"), "구급차 사이렌 소음이 있을 수 있어요"),
		new NearestRule(NoiseSource.FIRE_STATION, false, "소방서", 300, 0,
			category -> category.contains("소방서"), "소방차 사이렌 소음이 있을 수 있어요")
	);

	private final PlaceSearchClient placeSearchClient;

	/** 키가 없어 검색할 수 없으면 비어 있다("정보 없음"). */
	public Optional<NoiseResult> estimate(double latitude, double longitude) {
		if (!placeSearchClient.available()) {
			return Optional.empty();
		}
		List<NoiseWarning> warnings = new ArrayList<>();
		nearestAirport(latitude, longitude).ifPresent(warnings::add);
		for (NearestRule rule : NEAREST_RULES) {
			nearest(rule, latitude, longitude).ifPresent(warnings::add);
		}
		bars(latitude, longitude).ifPresent(warnings::add);
		return Optional.of(NoiseResult.of(warnings));
	}

	private Optional<NoiseWarning> nearestAirport(double latitude, double longitude) {
		Airports.Airport nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (Airports.Airport airport : Airports.ALL) {
			int distance = (int) Math.round(GeoDistance.meters(latitude, longitude, airport.latitude(), airport.longitude()));
			if (distance < nearestDistance) {
				nearest = airport;
				nearestDistance = distance;
			}
		}
		if (nearest == null || nearestDistance > AIRPORT_MEDIUM_WITHIN) {
			return Optional.empty();
		}
		NoiseGrade level = nearestDistance <= AIRPORT_HIGH_WITHIN ? NoiseGrade.HIGH : NoiseGrade.MEDIUM;
		return Optional.of(warning(NoiseSource.AIRPORT, level, nearest.name(), nearestDistance, "항공기 소음에 유의하세요"));
	}

	private Optional<NoiseWarning> nearest(NearestRule rule, double latitude, double longitude) {
		List<Place> places = rule.category()
			? placeSearchClient.searchCategory(rule.query(), latitude, longitude, rule.radius())
			: placeSearchClient.searchKeyword(rule.query(), latitude, longitude, rule.radius());
		return places.stream()
			.filter(place -> place.categoryName() != null && rule.categoryFilter().test(place.categoryName()))
			.filter(place -> place.distanceMeters() <= rule.radius())
			.min(Comparator.comparingInt(Place::distanceMeters))
			.map(place -> warning(rule.source(),
				place.distanceMeters() <= rule.highWithin() ? NoiseGrade.HIGH : NoiseGrade.MEDIUM,
				place.name(), place.distanceMeters(), rule.reason()));
	}

	/** 술집은 한 곳보다 몰려 있는지가 중요해 반경 안의 개수로 판단한다 (최대 45곳까지 센다). */
	private Optional<NoiseWarning> bars(double latitude, double longitude) {
		long count = placeSearchClient.searchKeyword("술집", latitude, longitude, BAR_RADIUS).stream()
			.filter(place -> place.categoryName() != null && place.categoryName().contains("> 술집"))
			.filter(place -> place.distanceMeters() <= BAR_RADIUS)
			.count();
		if (count < BAR_MEDIUM_COUNT) {
			return Optional.empty();
		}
		NoiseGrade level = count >= BAR_HIGH_COUNT ? NoiseGrade.HIGH : NoiseGrade.MEDIUM;
		String message = "%dm 안에 술집이 %d곳 있어 밤 소음에 유의하세요".formatted(BAR_RADIUS, count);
		return Optional.of(new NoiseWarning(NoiseSource.BAR_CLUSTER, level, null, null, (int) count, message));
	}

	private static NoiseWarning warning(NoiseSource source, NoiseGrade level, String name, int distance, String reason) {
		String message = "%s '%s'까지 %s — %s".formatted(source.label(), name, formatDistance(distance), reason);
		return new NoiseWarning(source, level, name, distance, null, message);
	}

	static String formatDistance(int meters) {
		return meters < 1_000 ? meters + "m" : String.format(Locale.ROOT, "%.1fkm", meters / 1_000.0);
	}

	/**
	 * @param category     true면 카테고리 그룹 코드 검색, false면 키워드 검색
	 * @param highWithin   이 거리 안이면 HIGH, 아니면 MEDIUM (0이면 항상 MEDIUM)
	 */
	private record NearestRule(
		NoiseSource source,
		boolean category,
		String query,
		int radius,
		int highWithin,
		Predicate<String> categoryFilter,
		String reason
	) {
	}
}
