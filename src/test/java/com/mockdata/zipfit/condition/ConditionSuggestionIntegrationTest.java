package com.mockdata.zipfit.condition;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.ai.AiClient;
import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** REC-03 (모든 후보지가 가파르고 차량 진입은 가능하다고 둔다) */
class ConditionSuggestionIntegrationTest extends ScenarioTestSupport {

	@MockitoSpyBean
	AiClient aiClient;

	@BeforeEach
	void steepButDrivable() {
		doReturn(new SlopeResponse(7.0, 10.5, SlopeGrade.STEEP, "성수역", "수치지형도", Confidence.LOW))
			.when(aiClient).estimateSlope(any());
		doReturn(new VehicleAccessResponse(VehicleAccess.POSSIBLE, "도로 폭 6m", "도로명주소", Confidence.HIGH))
			.when(aiClient).estimateVehicleAccess(any());
	}

	@Test
	@DisplayName("대화에 없던 경사 조건을 가파른 후보지를 근거로 제안하고, 받아들이면 조건 확정 초기값에 넣는다")
	void suggestAndAccept() throws Exception {
		Scenario s = analyzedWorkspace();
		String path = "/api/workspaces/%d/condition-suggestions".formatted(s.workspaceId());
		mockMvc.perform(bearer(get(path), s.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));

		Long candidateId = addCandidates(s, 1).getFirst();
		mockMvc.perform(bearer(get(path), s.guest()))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("SLOPE"))
			.andExpect(jsonPath("$.items[0].accepted").value(false))
			.andExpect(jsonPath("$.items[0].evidence[0].candidateId").value(candidateId))
			.andExpect(jsonPath("$.items[0].evidence[0].detail").value("성수역까지 최대 경사 10.5% (가파름, 추정치)"));

		mockMvc.perform(bearer(post(path + "/VEHICLE_ACCESS/accept"), s.guest()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.SUGGESTION_NOT_AVAILABLE.name()));
		mockMvc.perform(bearer(post(path + "/SLOPE/accept"), s.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].accepted").value(true));
		mockMvc.perform(bearer(get("/api/workspaces/%d/condition".formatted(s.workspaceId())), s.host()))
			.andExpect(jsonPath("$.draft.avoidSlope").value(true))
			.andExpect(jsonPath("$.draft.monthlyRentMax").value(60));
	}

	@Test
	@DisplayName("이미 확정한 조건이 있으면 받아들인 제안을 확정 조건에도 반영한다")
	void acceptAfterConfirm() throws Exception {
		Scenario s = analyzedWorkspace();
		addCandidates(s, 1);
		String conditionPath = "/api/workspaces/%d/condition".formatted(s.workspaceId());
		mockMvc.perform(json(bearer(post(conditionPath), s.host()), "{\"monthlyRentMax\":60}"));

		mockMvc.perform(bearer(post("/api/workspaces/%d/condition-suggestions/SLOPE/accept".formatted(s.workspaceId())), s.guest()))
			.andExpect(status().isOk());
		mockMvc.perform(bearer(get(conditionPath), s.host()))
			.andExpect(jsonPath("$.condition.avoidSlope").value(true))
			.andExpect(jsonPath("$.condition.monthlyRentMax").value(60))
			.andExpect(jsonPath("$.condition.lastModifiedBy.nickname").value("손님"));
	}

	@Test
	@DisplayName("대화에서 이미 나온 조건은 다시 제안하지 않는다")
	void skipMentionedConditions() throws Exception {
		Scenario s = analyzedWorkspace("성수 투룸 월세 60만, 오르막은 싫어");
		addCandidates(s, 2);

		mockMvc.perform(bearer(get("/api/workspaces/%d/condition-suggestions".formatted(s.workspaceId())), s.guest()))
			.andExpect(jsonPath("$.items.length()").value(0));
	}
}
