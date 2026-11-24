package com.mockdata.zipfit.ai;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ai.base-url이 있으면 AI 서버를 호출하고, 없으면 가짜 구현으로 동작한다. */
@Slf4j
@Configuration
public class AiClientConfig {

	@Bean
	public AiClient aiClient(
		@Value("${ai.base-url:}") String baseUrl,
		@Value("${ai.connect-timeout:3s}") Duration connectTimeout,
		@Value("${ai.read-timeout:30s}") Duration readTimeout
	) {
		if (baseUrl.isBlank()) {
			log.warn("ai.base-url이 없어 가짜 AI 응답(FakeAiClient)을 씁니다. 추천·입지 분석 결과는 실제 값이 아닙니다.");
			return new FakeAiClient();
		}
		return new HttpAiClient(baseUrl, connectTimeout, readTimeout);
	}
}
