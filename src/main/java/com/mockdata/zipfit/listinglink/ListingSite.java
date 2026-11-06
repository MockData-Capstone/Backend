package com.mockdata.zipfit.listinglink;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.condition.entity.HouseCondition;
import java.util.Set;

/**
 * 매물 검색 사이트별 링크 생성 (LINK-01). 매물 데이터는 수집하지 않고 링크로만 연결한다 (NFR-012).
 * 사이트가 지원하는 검색 파라미터는 미결(PRD Q6)이라, 확인된 조건만 supported에 넣는다.
 */
public interface ListingSite {

	String code();

	String displayName();

	/** 링크에 반영하는 조건. 나머지 조건은 링크에서 빼고 안내한다. */
	Set<ListingCondition> supported();

	String buildUrl(Candidate candidate, HouseCondition condition);

	/** 링크를 만들지 못하면 연결할 사이트 메인 페이지 */
	String fallbackUrl();
}
