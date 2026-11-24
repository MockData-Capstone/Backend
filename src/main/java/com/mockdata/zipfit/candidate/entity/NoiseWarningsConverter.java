package com.mockdata.zipfit.candidate.entity;

import com.mockdata.zipfit.location.noise.NoiseWarning;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 소음 주의 항목 목록을 text 칼럼에 JSON으로 저장한다. 검색이나 집계를 하지 않아 별도 테이블로 나누지 않는다. */
@Converter
public class NoiseWarningsConverter implements AttributeConverter<List<NoiseWarning>, String> {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final TypeReference<List<NoiseWarning>> TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<NoiseWarning> warnings) {
		return warnings == null ? null : JSON.writeValueAsString(warnings);
	}

	@Override
	public List<NoiseWarning> convertToEntityAttribute(String json) {
		return json == null || json.isBlank() ? null : JSON.readValue(json, TYPE);
	}
}
