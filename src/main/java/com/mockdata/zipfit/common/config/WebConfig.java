package com.mockdata.zipfit.common.config;

import com.mockdata.zipfit.common.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final LocalFileStorage localFileStorage;

	/** 로컬에 저장한 업로드 파일(프로필 이미지)을 /uploads/** 로 서빙한다. */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(LocalFileStorage.URL_PATH + "**")
			.addResourceLocations(localFileStorage.getRoot().toUri().toString());
	}
}
