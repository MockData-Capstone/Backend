package com.mockdata.zipfit.listinglink;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** LINK-01 */
class ListingLinkIntegrationTest extends ScenarioTestSupport {

	@Test
	@DisplayName("후보지 위치와 확정 조건으로 사이트별 링크를 만들고, 반영하지 못한 조건을 알려준다")
	void createLinks() throws Exception {
		Scenario s = analyzedWorkspace();
		Long candidateId = addCandidates(s, 1).getFirst();
		String path = "/api/workspaces/%d/listing-links".formatted(s.workspaceId());

		mockMvc.perform(bearer(get(path).param("candidateId", candidateId.toString()), s.guest()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.CONDITION_REQUIRED.name()));
		mockMvc.perform(json(bearer(post("/api/workspaces/%d/condition".formatted(s.workspaceId())), s.guest()),
			"{\"monthlyRentMax\":60,\"roomType\":\"TWO_ROOM\"}"));

		mockMvc.perform(bearer(get(path).param("candidateId", candidateId.toString()), s.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.region").value("성수역 인근 주택가"))
			.andExpect(jsonPath("$.links[?(@.site == 'NAVER_LAND')].url").value(
				contains(startsWith("https://new.land.naver.com/rooms?ms=37."))))
			.andExpect(jsonPath("$.links[?(@.site == 'NAVER_LAND')].appliedConditions[*]").value(
				contains("LOCATION")))
			.andExpect(jsonPath("$.links[?(@.site == 'NAVER_LAND')].excludedConditions[*]").value(
				containsInAnyOrder("MONTHLY_RENT", "ROOM_TYPE")))
			.andExpect(jsonPath("$.links[?(@.site == 'ZIGBANG')].fallback").value(contains(false)));

		mockMvc.perform(bearer(get(path).param("candidateId", "9999"), s.guest())).andExpect(status().isNotFound());
		mockMvc.perform(bearer(get(path), s.guest())).andExpect(status().isBadRequest());
		Long other = createWorkspace(s.host(), "다른방");
		mockMvc.perform(bearer(get("/api/workspaces/%d/listing-links".formatted(other)).param("candidateId", candidateId.toString()), s.host()))
			.andExpect(status().isNotFound());
	}
}
