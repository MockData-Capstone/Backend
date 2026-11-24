package com.mockdata.zipfit.recommendation.service;

import java.util.Collection;
import java.util.Map;

/**
 * 추천 위치가 후보지로 추가됐는지 알려준다. 후보지 도메인이 구현한다.
 * 후보지가 추천 위치를 참조하므로, 반대 방향은 이 인터페이스로만 묻는다.
 */
public interface CandidateLookup {

	/** 추천 위치 ID → 후보지 ID. 추가되지 않은 추천 위치는 결과에 없다. */
	Map<Long, Long> findCandidateIds(Long workspaceId, Collection<Long> recommendationIds);
}
