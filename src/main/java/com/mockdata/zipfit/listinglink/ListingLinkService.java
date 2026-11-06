package com.mockdata.zipfit.listinglink;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.condition.entity.HouseCondition;
import com.mockdata.zipfit.condition.service.ConditionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LINK-01 선택한 후보지의 지역과 확정 조건으로 사이트별 매물 검색 링크를 만든다.
 * 링크는 저장하지 않으며, 조건이나 후보지가 바뀌면 다시 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingLinkService {

	private final CandidateService candidateService;
	private final ConditionService conditionService;
	private final List<ListingSite> sites;

	@Transactional(readOnly = true)
	public ListingLinkResponse create(Long workspaceId, Long candidateId, Long userId) {
		Candidate candidate = candidateService.findMemberCandidate(candidateId, userId);
		if (!candidate.getWorkspace().getId().equals(workspaceId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND);
		}
		HouseCondition condition = conditionService.find(workspaceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONDITION_REQUIRED));
		List<ListingCondition> present = ListingCondition.presentIn(condition);
		return new ListingLinkResponse(candidateId, candidate.getName(),
			sites.stream().map(site -> link(site, candidate, condition, present)).toList());
	}

	/** 링크 생성에 실패하면 사이트 메인 페이지로 연결하고 오류를 기록한다. */
	private static ListingLinkResponse.Link link(
		ListingSite site, Candidate candidate, HouseCondition condition, List<ListingCondition> present
	) {
		try {
			return new ListingLinkResponse.Link(site.code(), site.displayName(), site.buildUrl(candidate, condition),
				present.stream().filter(site.supported()::contains).toList(),
				present.stream().filter(c -> !site.supported().contains(c)).toList(),
				false);
		} catch (RuntimeException e) {
			log.warn("매물 검색 링크 생성 실패: site={}, candidateId={}", site.code(), candidate.getId(), e);
			return new ListingLinkResponse.Link(site.code(), site.displayName(), site.fallbackUrl(), List.of(), present, true);
		}
	}
}
