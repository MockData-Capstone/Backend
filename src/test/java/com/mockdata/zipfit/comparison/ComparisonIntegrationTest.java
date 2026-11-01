package com.mockdata.zipfit.comparison;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CMP-01 */
class ComparisonIntegrationTest extends ScenarioTestSupport {

	@Test
	@DisplayName("후보지별 경사도·진입성·투표를 비교표로 만들고, 고른 후보지만 비교할 수도 있다")
	void compare() throws Exception {
		Scenario s = analyzedWorkspace();
		List<Long> candidates = addCandidates(s, 3);
		mockMvc.perform(json(bearer(post("/api/candidates/%d/votes".formatted(candidates.get(0))), s.guest()),
			"{\"choice\":\"AGREE\"}"));
		String path = "/api/workspaces/%d/comparisons".formatted(s.workspaceId());

		mockMvc.perform(bearer(get(path), s.host()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.generatedAt").exists())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].slope.grade").exists())
			.andExpect(jsonPath("$.items[0].slope.estimated").value(true))
			.andExpect(jsonPath("$.items[0].vehicleAccess.access").exists())
			.andExpect(jsonPath("$.items[0].votes.agreeCount").value(1));
		mockMvc.perform(bearer(post(path).param("candidateIds", "%d,%d".formatted(candidates.get(1), candidates.get(2))), s.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].candidateId").value(candidates.get(1)));
	}

	@Test
	@DisplayName("후보지가 2개 미만이거나 없는 후보지를 고르면 비교할 수 없다")
	void rejected() throws Exception {
		Scenario s = analyzedWorkspace();
		List<Long> candidates = addCandidates(s, 2);
		String path = "/api/workspaces/%d/comparisons".formatted(s.workspaceId());

		mockMvc.perform(bearer(get(path).param("candidateIds", candidates.get(0).toString()), s.host()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_ENOUGH_CANDIDATES.name()))
			.andExpect(jsonPath("$.message").value("후보지를 2개 이상 추가해주세요."));
		mockMvc.perform(bearer(get(path).param("candidateIds", "%d,9999".formatted(candidates.get(0))), s.host()))
			.andExpect(status().isNotFound());
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		mockMvc.perform(bearer(get(path), outsider)).andExpect(status().isForbidden());
	}
}
