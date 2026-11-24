package com.mockdata.zipfit.location.noise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mockdata.zipfit.location.place.PlaceSearchClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LOC-04 소음 추정 규칙 (외부 API 없이) */
class NoiseEstimatorTest {

	/** 반월당역 부근 (대구국제공항 여객터미널에서 약 5.6km) */
	private static final double LAT = 35.8644;
	private static final double LNG = 128.5934;

	@Test
	@DisplayName("주변에 소음원이 없으면 '낮음'이고 주의 항목이 없다")
	void quiet() {
		NoiseResult result = new NoiseEstimator(new FakePlaces(Map.of(), Map.of())).estimate(LAT, LNG).orElseThrow();

		assertThat(result.grade()).isEqualTo(NoiseGrade.LOW);
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("가까운 지하철역·학교는 '보통', 300m 안의 기차역은 '높음'이다. 이름만 비슷한 곳은 거른다")
	void nearestSources() {
		FakePlaces places = new FakePlaces(
			Map.of(
				"SW8", List.of(new PlaceSearchClient.Place("반월당역 대구1호선", "교통,수송 > 지하철,전철 > 대구1호선", 12)),
				"SC4", List.of(
					new PlaceSearchClient.Place("어느대학교", "교육,학문 > 학교 > 대학교", 50),
					new PlaceSearchClient.Place("대구초등학교", "교육,학문 > 학교 > 초등학교", 140))),
			Map.of(
				"기차역", List.of(new PlaceSearchClient.Place("대구역", "교통,수송 > 기차,철도 > 기차역", 280)),
				"응급실", List.of(new PlaceSearchClient.Place("응급실국물떡볶이", "음식점 > 분식 > 떡볶이", 100))));

		NoiseResult result = new NoiseEstimator(places).estimate(LAT, LNG).orElseThrow();

		assertThat(result.grade()).isEqualTo(NoiseGrade.HIGH);
		assertThat(result.warnings()).extracting(NoiseWarning::source)
			.containsExactly(NoiseSource.RAILWAY_STATION, NoiseSource.SUBWAY_STATION, NoiseSource.SCHOOL);
		assertThat(result.warnings().get(2).name()).isEqualTo("대구초등학교");
		assertThat(result.warnings().get(1).message())
			.isEqualTo("지하철역 '반월당역 대구1호선'까지 12m — 역 주변 유동 인구 소음이 있을 수 있어요");
	}

	@Test
	@DisplayName("술집은 200m 안에 5곳 이상이면 '보통', 15곳 이상이면 '높음'이다")
	void bars() {
		NoiseResult five = new NoiseEstimator(new FakePlaces(Map.of(), Map.of("술집", bars(5)))).estimate(LAT, LNG)
			.orElseThrow();
		assertThat(five.grade()).isEqualTo(NoiseGrade.MEDIUM);
		assertThat(five.warnings().get(0).count()).isEqualTo(5);
		assertThat(five.warnings().get(0).message()).isEqualTo("200m 안에 술집이 5곳 있어 밤 소음에 유의하세요");

		assertThat(new NoiseEstimator(new FakePlaces(Map.of(), Map.of("술집", bars(4)))).estimate(LAT, LNG)
			.orElseThrow().warnings()).isEmpty();
		assertThat(new NoiseEstimator(new FakePlaces(Map.of(), Map.of("술집", bars(15)))).estimate(LAT, LNG)
			.orElseThrow().grade()).isEqualTo(NoiseGrade.HIGH);
	}

	@Test
	@DisplayName("공항이 3km 안이면 '높음', 5km 안이면 '보통'이다 (전국 공항 목록)")
	void airport() {
		NoiseEstimator estimator = new NoiseEstimator(new FakePlaces(Map.of(), Map.of()));

		NoiseResult nearDaeguAirport = estimator.estimate(35.8840, 128.6400).orElseThrow();
		assertThat(nearDaeguAirport.grade()).isEqualTo(NoiseGrade.HIGH);
		assertThat(nearDaeguAirport.warnings().get(0).name()).isEqualTo("대구국제공항");

		// 김포공항에서 약 3.9km
		NoiseResult gimpo = estimator.estimate(37.5400, 126.8400).orElseThrow();
		assertThat(gimpo.warnings()).extracting(NoiseWarning::source).containsExactly(NoiseSource.AIRPORT);
		assertThat(gimpo.grade()).isEqualTo(NoiseGrade.MEDIUM);

		assertThat(estimator.estimate(LAT, LNG).orElseThrow().warnings()).isEmpty();
	}

	@Test
	@DisplayName("키가 없으면 추정하지 않고, 검색이 실패하면 예외를 던져 '정보 없음'으로 둔다")
	void unavailableOrFailed() {
		FakePlaces unavailable = new FakePlaces(Map.of(), Map.of());
		unavailable.available = false;
		assertThat(new NoiseEstimator(unavailable).estimate(LAT, LNG)).isEmpty();

		FakePlaces failing = new FakePlaces(Map.of(), Map.of());
		failing.fail = true;
		assertThatThrownBy(() -> new NoiseEstimator(failing).estimate(LAT, LNG)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("거리 표시: 1km 미만은 m, 이상은 소수 첫째 자리 km")
	void formatDistance() {
		assertThat(NoiseEstimator.formatDistance(999)).isEqualTo("999m");
		assertThat(NoiseEstimator.formatDistance(2_430)).isEqualTo("2.4km");
	}

	private static List<PlaceSearchClient.Place> bars(int count) {
		List<PlaceSearchClient.Place> bars = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			bars.add(new PlaceSearchClient.Place("술집" + i, "음식점 > 술집 > 호프,요리주점", 50 + i));
		}
		return bars;
	}

	private static final class FakePlaces implements PlaceSearchClient {

		private final Map<String, List<Place>> byCategory;
		private final Map<String, List<Place>> byKeyword;
		boolean available = true;
		boolean fail;

		FakePlaces(Map<String, List<Place>> byCategory, Map<String, List<Place>> byKeyword) {
			this.byCategory = byCategory;
			this.byKeyword = byKeyword;
		}

		@Override
		public boolean available() {
			return available;
		}

		@Override
		public List<Place> searchCategory(String code, double latitude, double longitude, int radius) {
			if (fail) {
				throw new IllegalStateException("카카오 장애");
			}
			return byCategory.getOrDefault(code, List.of());
		}

		@Override
		public List<Place> searchKeyword(String query, double latitude, double longitude, int radius) {
			if (fail) {
				throw new IllegalStateException("카카오 장애");
			}
			return byKeyword.getOrDefault(query, List.of());
		}
	}
}
