package com.mockdata.zipfit.candidate.service;

import com.mockdata.zipfit.candidate.dto.VoteSummary;
import java.util.Collection;
import java.util.Map;

/**
 * 후보지별 투표 집계. 투표 도메인이 구현한다.
 * 투표가 후보지를 참조하므로, 반대 방향은 이 인터페이스로만 묻는다.
 */
public interface VoteSummaryProvider {

	/** 후보지 ID → 집계. 투표가 없는 후보지는 결과에 없어도 된다(0표로 본다). */
	Map<Long, VoteSummary> summarize(Collection<Long> candidateIds, Long viewerUserId);
}
