package com.mockdata.zipfit.ai;

import com.mockdata.zipfit.ai.dto.AiRecommendationRequest;
import com.mockdata.zipfit.ai.dto.AiRecommendationResponse;
import com.mockdata.zipfit.ai.dto.SlopeRequest;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccessRequest;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 자체 GPU 서버의 AI 서버를 HTTP로 호출한다. ai.base-url이 있을 때 쓴다. */
@Slf4j
public class HttpAiClient implements AiClient {

	private final RestClient restClient;

	public HttpAiClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build();
	}

	@Override
	public AiRecommendationResponse recommend(AiRecommendationRequest request) {
		return post("/api/ai/recommendations", request, AiRecommendationResponse.class);
	}

	@Override
	public SlopeResponse estimateSlope(SlopeRequest request) {
		return post("/api/ai/slope", request, SlopeResponse.class);
	}

	@Override
	public VehicleAccessResponse estimateVehicleAccess(VehicleAccessRequest request) {
		return post("/api/ai/vehicle-access", request, VehicleAccessResponse.class);
	}

	private <T> T post(String path, Object body, Class<T> responseType) {
		try {
			T response = restClient.post()
				.uri(path)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(responseType);
			if (response == null) {
				throw new AiServerException(path + " 응답이 비어 있습니다.");
			}
			return response;
		} catch (RestClientException e) {
			log.warn("AI 서버 호출 실패: {}", path, e);
			throw new AiServerException(path + " 호출 실패", e);
		}
	}
}
