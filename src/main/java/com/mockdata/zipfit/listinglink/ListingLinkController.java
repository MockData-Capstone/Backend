package com.mockdata.zipfit.listinglink;

import com.mockdata.zipfit.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API명세서에 행이 없어 기능명세서(LINK-01·02)를 보고 경로를 정했다. 저장하지 않으므로 GET이다. */
@RestController
@RequiredArgsConstructor
public class ListingLinkController {

	private final ListingLinkService listingLinkService;

	/** LINK-01 선택한 후보지와 확정 조건으로 사이트별 매물 검색 링크를 만든다. */
	@GetMapping("/api/workspaces/{workspaceId}/listing-links")
	public ListingLinkResponse create(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@RequestParam Long candidateId
	) {
		return listingLinkService.create(workspaceId, candidateId, userId);
	}
}
