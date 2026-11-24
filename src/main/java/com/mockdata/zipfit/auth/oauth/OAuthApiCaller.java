package com.mockdata.zipfit.auth.oauth;

import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 소셜 제공자 API를 호출하고 응답 JSON을 파싱한다. 어떤 이유로든 실패하면 OAUTH_FAILED로 바꾼다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthApiCaller {

	private final JsonMapper jsonMapper;

	public <T> T call(String step, Supplier<String> request, Class<T> responseType) {
		String body;
		try {
			body = request.get();
		} catch (RestClientResponseException e) {
			log.info("{} 실패: status={}, body={}", step, e.getStatusCode(), e.getResponseBodyAsString());
			throw new BusinessException(ErrorCode.OAUTH_FAILED);
		} catch (RestClientException e) {
			log.warn("{} 요청 오류", step, e);
			throw new BusinessException(ErrorCode.OAUTH_FAILED);
		}
		if (body == null) {
			throw new BusinessException(ErrorCode.OAUTH_FAILED);
		}
		try {
			return jsonMapper.readValue(body, responseType);
		} catch (JacksonException e) {
			log.warn("{} 응답 파싱 실패: {}", step, body, e);
			throw new BusinessException(ErrorCode.OAUTH_FAILED);
		}
	}
}
