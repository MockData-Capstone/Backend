package com.mockdata.zipfit.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.AuthTestSupport;
import com.mockdata.zipfit.chat.service.ChatService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Workspace·분석·후보지까지 만들어 두는 시나리오 테스트 공통 설정 (AI 서버 대신 FakeAiClient) */
public abstract class ScenarioTestSupport extends AuthTestSupport {

	@Autowired
	protected ChatService chatService;

	/** 호스트·손님이 있고, 대화 분석이 끝나 추천 위치 3곳이 있는 Workspace */
	protected Scenario analyzedWorkspace() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "시나리오방");
		String guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		Long hostId = userIdOf("host@zipfit.com");
		chatService.send(workspaceId, hostId, "c-1", "성수 투룸 월세 60만");

		String path = "/api/workspaces/%d/recommendations".formatted(workspaceId);
		mockMvc.perform(bearer(post(path), host)).andExpect(status().isAccepted());
		Instant deadline = Instant.now().plusSeconds(5);
		String body;
		do {
			assertThat(Instant.now()).as("5초 안에 분석이 끝나야 한다").isBefore(deadline);
			Thread.sleep(50);
			body = mockMvc.perform(bearer(get(path), host)).andReturn().getResponse().getContentAsString();
		} while ("IN_PROGRESS".equals(JsonPath.read(body, "$.analysis.status")));
		List<Integer> ids = JsonPath.read(body, "$.items[*].recommendationId");
		return new Scenario(host, guest, workspaceId, ids.stream().map(Integer::longValue).toList());
	}

	/** 추천 위치 count곳을 후보지로 추가하고 후보지 ID를 돌려준다. */
	protected List<Long> addCandidates(Scenario scenario, int count) throws Exception {
		List<Long> candidateIds = new ArrayList<>();
		for (Long recommendationId : scenario.recommendationIds().subList(0, count)) {
			String body = mockMvc.perform(json(bearer(post("/api/workspaces/%d/candidates".formatted(scenario.workspaceId())),
					scenario.host()), "{\"recommendationId\":%d}".formatted(recommendationId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
			candidateIds.add(((Number) JsonPath.read(body, "$.candidateId")).longValue());
		}
		return candidateIds;
	}

	protected Long createWorkspace(String token, String name) throws Exception {
		String body = mockMvc.perform(json(bearer(post("/api/workspaces"), token), "{\"name\":\"%s\"}".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	/** 새 회원을 가입시키고 초대 링크로 Workspace에 참여시킨다. */
	protected String joinMember(String hostToken, Long workspaceId, String email, String nickname) throws Exception {
		String token = signupLocal(email, "password1", nickname).accessToken();
		String invitation = mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(workspaceId)), hostToken))
			.andReturn().getResponse().getContentAsString();
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(JsonPath.<String>read(invitation, "$.token"))), token))
			.andExpect(status().isOk());
		return token;
	}

	protected Long userIdOf(String email) {
		return userRepository.findByEmailAndPasswordIsNotNull(email).orElseThrow().getId();
	}

	protected record Scenario(String host, String guest, Long workspaceId, List<Long> recommendationIds) {
	}
}
