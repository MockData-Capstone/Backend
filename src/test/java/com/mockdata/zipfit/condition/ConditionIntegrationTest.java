package com.mockdata.zipfit.condition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** COND-01~03 */
class ConditionIntegrationTest extends ScenarioTestSupport {

	@Test
	@DisplayName("확정 전에는 AI 분석에서 추출한 조건을 초기값으로 주고, 확정하면 Workspace당 1건만 남는다")
	void confirm() throws Exception {
		Scenario s = analyzedWorkspace();
		String path = "/api/workspaces/%d/condition".formatted(s.workspaceId());

		mockMvc.perform(bearer(get(path), s.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.condition").value((Object) null))
			.andExpect(jsonPath("$.draft.roomType").value("TWO_ROOM"))
			.andExpect(jsonPath("$.draft.monthlyRentMax").value(60));
		mockMvc.perform(json(bearer(put(path), s.guest()), "{\"monthlyRentMax\":60}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.CONDITION_NOT_FOUND.name()));

		mockMvc.perform(json(bearer(post(path), s.guest()),
				"{\"depositMax\":1000,\"monthlyRentMin\":40,\"monthlyRentMax\":60,\"roomType\":\"TWO_ROOM\",\"areaMin\":33}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.monthlyRentMax").value(60))
			.andExpect(jsonPath("$.lastModifiedBy.nickname").value("손님"));
		mockMvc.perform(json(bearer(post(path), s.host()), "{\"monthlyRentMax\":70}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.CONDITION_ALREADY_EXISTS.name()));
	}

	@Test
	@DisplayName("수정은 화면 값 전체로 바꾸고 마지막에 수정한 멤버를 남기며, 최솟값이 최댓값보다 크면 저장하지 않는다")
	void update() throws Exception {
		Scenario s = analyzedWorkspace();
		String path = "/api/workspaces/%d/condition".formatted(s.workspaceId());
		mockMvc.perform(json(bearer(post(path), s.guest()), "{\"monthlyRentMax\":60,\"roomType\":\"TWO_ROOM\"}"));

		mockMvc.perform(json(bearer(put(path), s.host()), "{\"monthlyRentMin\":70,\"monthlyRentMax\":60}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_RANGE.name()))
			.andExpect(jsonPath("$.message").value("월세 최솟값이 최댓값보다 클 수 없습니다."));
		mockMvc.perform(json(bearer(put(path), s.host()), "{\"depositMin\":-1}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(json(bearer(put(path), s.host()), "{\"monthlyRentMax\":80,\"areaMin\":20,\"areaMax\":40}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.monthlyRentMax").value(80))
			.andExpect(jsonPath("$.roomType").doesNotExist())
			.andExpect(jsonPath("$.lastModifiedBy.nickname").value("호스트"));
		mockMvc.perform(bearer(get(path), s.guest()))
			.andExpect(jsonPath("$.condition.areaMax").value(40.0));

		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		mockMvc.perform(bearer(get(path), outsider)).andExpect(status().isForbidden());
		mockMvc.perform(bearer(delete("/api/workspaces/" + s.workspaceId()), s.host())).andExpect(status().isNoContent());
	}
}
