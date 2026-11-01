package com.mockdata.zipfit.vote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import com.mockdata.zipfit.vote.repository.VoteRepository;
import com.mockdata.zipfit.vote.service.VoteService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** VOTE-01~03 */
@RecordApplicationEvents
class VoteIntegrationTest extends ScenarioTestSupport {

	@Autowired
	VoteRepository voteRepository;
	@Autowired
	ApplicationEvents events;

	@Test
	@DisplayName("VOTE-01·03 후보지마다 1표를 남기고, 다시 투표하려면 변경을 쓴다")
	void voteAndChange() throws Exception {
		Scenario s = analyzedWorkspace();
		Long candidateId = addCandidates(s, 1).getFirst();

		vote(s.host(), candidateId, "AGREE")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.agreeCount").value(1))
			.andExpect(jsonPath("$.myChoice").value("AGREE"))
			.andExpect(jsonPath("$.votes[0].nickname").value("호스트"));
		vote(s.host(), candidateId, "DISAGREE")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_VOTED.name()));
		change(s.guest(), candidateId, "AGREE")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.VOTE_NOT_FOUND.name()));

		vote(s.guest(), candidateId, "DISAGREE")
			.andExpect(jsonPath("$.agreeCount").value(1))
			.andExpect(jsonPath("$.disagreeCount").value(1))
			.andExpect(jsonPath("$.split").value(true));
		change(s.guest(), candidateId, "AGREE")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agreeCount").value(2))
			.andExpect(jsonPath("$.split").value(false))
			.andExpect(jsonPath("$.myChoice").value("AGREE"));
		assertThat(events.stream(WorkspaceEvent.class).filter(e -> e.type().equals(VoteService.VOTE_UPDATED))).hasSize(3);

		vote(s.host(), candidateId, "MAYBE").andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("VOTE-02 후보지별 집계와 내 표를 보여주고, 투표가 없는 후보지는 0표다")
	void results() throws Exception {
		Scenario s = analyzedWorkspace();
		List<Long> candidates = addCandidates(s, 2);
		vote(s.guest(), candidates.get(0), "AGREE");

		mockMvc.perform(bearer(get("/api/workspaces/%d/votes".formatted(s.workspaceId())), s.host()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].candidateId").value(candidates.get(0)))
			.andExpect(jsonPath("$.items[0].agreeCount").value(1))
			.andExpect(jsonPath("$.items[0].myChoice").doesNotExist())
			.andExpect(jsonPath("$.items[1].agreeCount").value(0))
			.andExpect(jsonPath("$.items[1].votes.length()").value(0));
		mockMvc.perform(bearer(get("/api/workspaces/%d/candidates".formatted(s.workspaceId())), s.guest()))
			.andExpect(jsonPath("$.items[0].votes.agreeCount").value(1))
			.andExpect(jsonPath("$.items[0].votes.myChoice").value("AGREE"));

		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		mockMvc.perform(bearer(get("/api/workspaces/%d/votes".formatted(s.workspaceId())), outsider))
			.andExpect(status().isForbidden());
		vote(outsider, candidates.get(0), "AGREE").andExpect(status().isForbidden());
		vote(s.host(), 9999L, "AGREE").andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("나가거나 내보내진 멤버의 표와 제외된 후보지의 표는 집계에서 빠진다")
	void votesRemoved() throws Exception {
		Scenario s = analyzedWorkspace();
		List<Long> candidates = addCandidates(s, 2);
		vote(s.guest(), candidates.get(0), "AGREE");
		vote(s.host(), candidates.get(1), "AGREE");

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(s.workspaceId())), s.guest()))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(delete("/api/candidates/" + candidates.get(1)), s.host()))
			.andExpect(status().isNoContent());

		mockMvc.perform(bearer(get("/api/workspaces/%d/votes".formatted(s.workspaceId())), s.host()))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].agreeCount").value(0));
		assertThat(voteRepository.count()).isZero();

		mockMvc.perform(bearer(delete("/api/workspaces/" + s.workspaceId()), s.host())).andExpect(status().isNoContent());
	}

	private ResultActions vote(String token, Long candidateId, String choice) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/candidates/%d/votes".formatted(candidateId)), token),
			"{\"choice\":\"%s\"}".formatted(choice)));
	}

	private ResultActions change(String token, Long candidateId, String choice) throws Exception {
		return mockMvc.perform(json(bearer(patch("/api/candidates/%d/votes/me".formatted(candidateId)), token),
			"{\"choice\":\"%s\"}".formatted(choice)));
	}
}
