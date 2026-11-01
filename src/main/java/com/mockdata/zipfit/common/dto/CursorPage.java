package com.mockdata.zipfit.common.dto;

import java.util.List;

/**
 * 커서로 나누는 목록 응답.
 *
 * @param nextCursor 다음 페이지를 받을 때 그대로 넘기는 값. hasNext가 false면 null이다.
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {
}
