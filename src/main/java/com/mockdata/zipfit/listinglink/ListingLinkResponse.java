package com.mockdata.zipfit.listinglink;

import java.util.List;

/**
 * LINK-01 매물 검색 링크. 저장하지 않고 요청할 때 만든다. 프론트는 url을 새 탭으로 연다 (LINK-02).
 *
 * @param region 후보지 이름 (지역은 후보지에서 가져온다)
 */
public record ListingLinkResponse(Long candidateId, String region, List<Link> links) {

	/**
	 * @param appliedConditions  링크에 반영한 조건
	 * @param excludedConditions 확정했지만 사이트가 지원하지 않아 링크에서 뺀 조건. 사이트에서 직접 설정하도록 안내한다
	 * @param fallback           링크를 만들지 못해 사이트 메인으로 연결했으면 true
	 */
	public record Link(
		String site,
		String siteName,
		String url,
		List<ListingCondition> appliedConditions,
		List<ListingCondition> excludedConditions,
		boolean fallback
	) {
	}
}
