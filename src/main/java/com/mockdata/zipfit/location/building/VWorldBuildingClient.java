package com.mockdata.zipfit.location.building;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 브이월드 데이터 API의 GIS건물통합정보(LT_C_BLDGINFO). https://www.vworld.kr/dev/v4dv_2ddataguide2_s001.do
 * 인증키는 서비스 URL 하나에 묶여 있어, 어디서 부르든 등록한 주소를 domain으로 보낸다 (로컬 개발도 같다).
 */
public class VWorldBuildingClient implements BuildingClient {

	private static final String BASE_URL = "https://api.vworld.kr/req/data";
	private static final int PAGE_SIZE = 1000;
	private static final int MAX_PAGES = 5;
	/** 높이(height)가 비어 있는 건물이 많아 층수로 추정한다. 층수도 없으면 1층으로 본다. */
	static final double FLOOR_HEIGHT_METERS = 3.0;

	private final RestClient restClient;
	private final String apiKey;
	private final String domain;

	public VWorldBuildingClient(String apiKey, String domain, Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder()
			.baseUrl(BASE_URL)
			.requestFactory(requestFactory)
			.build();
		this.apiKey = apiKey;
		this.domain = domain;
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<Building> findBuildings(double latitude, double longitude, int radiusMeters) {
		double dLat = radiusMeters / 110_574.0;
		double dLng = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(latitude)));
		String box = String.format(Locale.ROOT, "BOX(%.6f,%.6f,%.6f,%.6f)",
			longitude - dLng, latitude - dLat, longitude + dLng, latitude + dLat);

		List<Building> buildings = new ArrayList<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			int currentPage = page;
			Wrapper wrapper = restClient.get()
				.uri(uri -> uri
					.queryParam("service", "data")
					.queryParam("version", "2.0")
					.queryParam("request", "GetFeature")
					.queryParam("data", "LT_C_BLDGINFO")
					.queryParam("format", "json")
					.queryParam("crs", "EPSG:4326")
					.queryParam("geometry", true)
					.queryParam("attribute", true)
					.queryParam("geomFilter", box)
					.queryParam("size", PAGE_SIZE)
					.queryParam("page", currentPage)
					.queryParam("key", apiKey)
					.queryParam("domain", domain)
					.build())
				.retrieve()
				.body(Wrapper.class);
			Response response = wrapper == null ? null : wrapper.response();
			if (response == null) {
				throw new IllegalStateException("브이월드 건물정보 응답이 비어 있습니다.");
			}
			if ("NOT_FOUND".equals(response.status())) {
				break;
			}
			if (!"OK".equals(response.status())) {
				String error = response.error() == null ? response.status() : response.error().code() + " " + response.error().text();
				throw new IllegalStateException("브이월드 건물정보 조회 실패: " + error);
			}
			if (response.result() != null && response.result().featureCollection() != null
				&& response.result().featureCollection().features() != null) {
				for (Feature feature : response.result().featureCollection().features()) {
					addBuildings(feature, buildings);
				}
			}
			if (response.page() == null || parseInt(response.page().current()) >= parseInt(response.page().total())) {
				break;
			}
		}
		return buildings;
	}

	/** MultiPolygon의 다각형마다 바깥 고리(첫 번째 고리)만 쓴다. 안뜰(구멍)은 무시한다. */
	private static void addBuildings(Feature feature, List<Building> buildings) {
		if (feature.geometry() == null || feature.geometry().coordinates() == null) {
			return;
		}
		double height = heightOf(feature.properties());
		for (List<List<List<Double>>> polygon : feature.geometry().coordinates()) {
			if (polygon.isEmpty() || polygon.get(0).size() < 3) {
				continue;
			}
			List<double[]> outline = new ArrayList<>(polygon.get(0).size());
			for (List<Double> point : polygon.get(0)) {
				outline.add(new double[] {point.get(0), point.get(1)});
			}
			buildings.add(new Building(outline, height));
		}
	}

	static double heightOf(Properties properties) {
		if (properties == null) {
			return FLOOR_HEIGHT_METERS;
		}
		double height = parseDouble(properties.height());
		if (height > 0) {
			return height;
		}
		int floors = parseInt(properties.groundFloors());
		return Math.max(floors, 1) * FLOOR_HEIGHT_METERS;
	}

	private static double parseDouble(String value) {
		try {
			return value == null || value.isBlank() ? 0 : Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int parseInt(String value) {
		try {
			return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Wrapper(Response response) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(String status, Error error, Page page, Result result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Error(String code, String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Page(String total, String current) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(FeatureCollection featureCollection) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record FeatureCollection(List<Feature> features) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Feature(Geometry geometry, Properties properties) {
	}

	/** MultiPolygon: 다각형 > 고리 > 점 > {경도, 위도} */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Geometry(String type, List<List<List<List<Double>>>> coordinates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Properties(String height, @JsonProperty("grnd_flr") String groundFloors) {
	}
}
