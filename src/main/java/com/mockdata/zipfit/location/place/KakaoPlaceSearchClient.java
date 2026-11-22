package com.mockdata.zipfit.location.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 장소 검색. 카카오 로그인과 같은 REST API 키를 쓴다.
 * 한 번에 15개씩 최대 3쪽(45개)까지 받는다. https://developers.kakao.com/docs/latest/ko/local/dev-guide
 */
public class KakaoPlaceSearchClient implements PlaceSearchClient {

	private static final String BASE_URL = "https://dapi.kakao.com/v2/local/search";
	private static final int PAGE_SIZE = 15;
	private static final int MAX_PAGES = 3;

	private final RestClient restClient;

	public KakaoPlaceSearchClient(String restApiKey, Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder()
			.baseUrl(BASE_URL)
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
			.build();
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public List<Place> searchCategory(String categoryGroupCode, double latitude, double longitude, int radiusMeters) {
		return search("/category.json", "category_group_code", categoryGroupCode, latitude, longitude, radiusMeters);
	}

	@Override
	public List<Place> searchKeyword(String query, double latitude, double longitude, int radiusMeters) {
		return search("/keyword.json", "query", query, latitude, longitude, radiusMeters);
	}

	private List<Place> search(String path, String param, String value, double latitude, double longitude, int radius) {
		List<Place> places = new ArrayList<>();
		for (int page = 1; page <= MAX_PAGES; page++) {
			int currentPage = page;
			SearchResponse response = restClient.get()
				.uri(uri -> uri.path(path)
					.queryParam(param, value)
					.queryParam("x", longitude)
					.queryParam("y", latitude)
					.queryParam("radius", radius)
					.queryParam("sort", "distance")
					.queryParam("page", currentPage)
					.queryParam("size", PAGE_SIZE)
					.build())
				.retrieve()
				.body(SearchResponse.class);
			if (response == null || response.documents() == null) {
				break;
			}
			for (Document doc : response.documents()) {
				places.add(new Place(doc.placeName(), doc.categoryName(), parseDistance(doc.distance())));
			}
			if (response.meta() == null || response.meta().isEnd()) {
				break;
			}
		}
		return places;
	}

	private static int parseDistance(String distance) {
		try {
			return distance == null || distance.isBlank() ? Integer.MAX_VALUE : Integer.parseInt(distance);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchResponse(List<Document> documents, Meta meta) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Document(
		@JsonProperty("place_name") String placeName,
		@JsonProperty("category_name") String categoryName,
		String distance
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Meta(@JsonProperty("is_end") boolean isEnd) {
	}
}
