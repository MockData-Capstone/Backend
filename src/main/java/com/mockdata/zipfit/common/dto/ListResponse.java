package com.mockdata.zipfit.common.dto;

import java.util.List;

/** 페이지를 나누지 않는 목록 응답. 비어 있으면 items는 빈 배열이다. */
public record ListResponse<T>(List<T> items) {
}
