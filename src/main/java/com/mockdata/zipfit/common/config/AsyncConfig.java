package com.mockdata.zipfit.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 메일 발송 같은 비동기 작업과, 만료된 WebSocket 연결 정리 같은 주기 작업 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
