package com.mockdata.zipfit.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.ai.AiClient;
import com.mockdata.zipfit.ai.AiServerException;
import com.mockdata.zipfit.auth.AuthTestSupport;
import com.mockdata.zipfit.candidate.repository.CandidateRepository;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.recommendation.entity.Recommendation;
import com.mockdata.zipfit.recommendation.repository.AnalysisRepository;
import com.mockdata.zipfit.recommendation.repository.RecommendationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** CAND-01~04, LOC-03 */
@RecordApplicationEvents
class CandidateIntegrationTest extends AuthTestSupport {

	@MockitoSpyBean
	AiClient aiClient;
	@Autowired
	ChatService chatService;
	@Autowired
	CandidateRepository candidateRepository;
	@Autowired
	AnalysisRepository analysisRepository;
	@Autowired
	RecommendationRepository recommendationRepository;
	@Autowired
	ApplicationEvents events;

	@Test
	@DisplayName("CAND-01 추천 위치를 후보지로 추가하면 입지 정보를 산출하고, 같은 위치는 다시 추가할 수 없다")
	void add() throws Exception {
		Fixture f = analyzedWorkspace();

		add(f.host(), f.workspaceId(), f.recommendationIds().get(0))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("성수역 인근 주택가"))
			.andExpect(jsonPath("$.latitude").exists())
			.andExpect(jsonPath("$.addedBy.nickname").value("호스트"))
			.andExpect(jsonPath("$.location.slope.grade").exists())
			.andExpect(jsonPath("$.location.slope.estimated").value(true))
			.andExpect(jsonPath("$.location.vehicleAccess.access").exists())
			.andExpect(jsonPath("$.votes.agreeCount").value(0));
		add(f.guest(), f.workspaceId(), f.recommendationIds().get(0))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_CANDIDATE.name()));
		assertThat(eventTypes()).containsOnlyOnce(CandidateService.CANDIDATE_ADDED);

		mockMvc.perform(bearer(get("/api/workspaces/%d/recommendations".formatted(f.workspaceId())), f.guest()))
			.andExpect(jsonPath("$.items[0].addedAsCandidate").value(true))
			.andExpect(jsonPath("$.items[0].candidateId").exists())
			.andExpect(jsonPath("$.items[1].addedAsCandidate").value(false));
		mockMvc.perform(bearer(get("/api/workspaces/%d/candidates".formatted(f.workspaceId())), f.guest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].location.slope.averagePercent").exists());
	}

	@Test
	@DisplayName("CAND-01 좌표가 없거나 다른 Workspace의 추천 위치는 추가할 수 없다")
	void addRejected() throws Exception {
		Fixture f = analyzedWorkspace();
		Recommendation noCoordinates = recommendationRepository.save(new Recommendation(
			analysisRepository.findFirstByWorkspaceIdOrderByIdDesc(f.workspaceId()).orElseThrow(),
			"좌표 없는 곳", null, null, "이유", null));

		add(f.host(), f.workspaceId(), noCoordinates.getId())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.NO_COORDINATES.name()));
		Long otherWorkspace = createWorkspace(f.host(), "다른방");
		add(f.host(), otherWorkspace, f.recommendationIds().get(0))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.RECOMMENDATION_NOT_FOUND.name()));
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		add(outsider, f.workspaceId(), f.recommendationIds().get(0))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("LOC 입지 분석에 실패한 항목은 비워 두고 후보지는 추가된다")
	void locationFailureIsolated() throws Exception {
		Fixture f = analyzedWorkspace();
		doThrow(new AiServerException("down")).when(aiClient).estimateSlope(any());

		add(f.host(), f.workspaceId(), f.recommendationIds().get(0))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.location.slope").doesNotExist())
			.andExpect(jsonPath("$.location.vehicleAccess.access").exists());
	}

	@Test
	@DisplayName("CAND-04 멤버는 이름을 바꿀 수 있고, 비우면 바꿀 수 없다")
	void rename() throws Exception {
		Fixture f = analyzedWorkspace();
		Long candidateId = candidateIdOf(add(f.host(), f.workspaceId(), f.recommendationIds().get(0)));

		mockMvc.perform(json(bearer(patch("/api/candidates/" + candidateId), f.guest()), "{\"name\":\" 우리 1순위 \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("우리 1순위"))
			.andExpect(jsonPath("$.placeName").value("성수역 인근 주택가"));
		mockMvc.perform(json(bearer(patch("/api/candidates/" + candidateId), f.guest()), "{\"name\":\" \"}"))
			.andExpect(status().isBadRequest());
		assertThat(eventTypes()).contains(CandidateService.CANDIDATE_UPDATED);
	}

	@Test
	@DisplayName("CAND-03 추가한 멤버나 호스트만 제외할 수 있고, 이미 제외된 후보지는 404다")
	void remove() throws Exception {
		Fixture f = analyzedWorkspace();
		String third = joinedMember(f, "third@zipfit.com", "셋째");
		Long byGuest = candidateIdOf(add(f.guest(), f.workspaceId(), f.recommendationIds().get(0)));
		Long byGuest2 = candidateIdOf(add(f.guest(), f.workspaceId(), f.recommendationIds().get(1)));

		mockMvc.perform(bearer(delete("/api/candidates/" + byGuest), third))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_CANDIDATE_OWNER.name()));
		mockMvc.perform(bearer(delete("/api/candidates/" + byGuest), f.guest()))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(delete("/api/candidates/" + byGuest), f.guest()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(ErrorCode.CANDIDATE_NOT_FOUND.name()));
		mockMvc.perform(bearer(delete("/api/candidates/" + byGuest2), f.host()))
			.andExpect(status().isNoContent());
		assertThat(eventTypes()).contains(CandidateService.CANDIDATE_REMOVED);
		assertThat(candidateRepository.count()).isZero();
	}

	@Test
	@DisplayName("추가한 멤버가 탈퇴해도 후보지는 남고, Workspace를 삭제하면 함께 지워진다")
	void lifecycle() throws Exception {
		Fixture f = analyzedWorkspace();
		add(f.guest(), f.workspaceId(), f.recommendationIds().get(0)).andExpect(status().isCreated());

		mockMvc.perform(bearer(delete("/api/users/me"), f.guest())).andExpect(status().isNoContent());
		mockMvc.perform(bearer(get("/api/workspaces/%d/candidates".formatted(f.workspaceId())), f.host()))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].addedBy").doesNotExist());

		mockMvc.perform(bearer(delete("/api/workspaces/" + f.workspaceId()), f.host())).andExpect(status().isNoContent());
		assertThat(candidateRepository.count()).isZero();
		assertThat(analysisRepository.count()).isZero();
	}

	/** 호스트와 손님이 있고, 성수 대화로 분석이 끝난 Workspace */
	private Fixture analyzedWorkspace() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "후보지방");
		Fixture partial = new Fixture(host, null, workspaceId, List.of());
		String guest = joinedMember(partial, "guest@zipfit.com", "손님");
		Long hostId = userRepository.findByEmailAndPasswordIsNotNull("host@zipfit.com").orElseThrow().getId();
		chatService.send(workspaceId, hostId, "c-1", "성수 투룸 월세 60만");

		String path = "/api/workspaces/%d/recommendations".formatted(workspaceId);
		mockMvc.perform(bearer(post(path), host)).andExpect(status().isAccepted());
		Instant deadline = Instant.now().plusSeconds(5);
		String body;
		do {
			assertThat(Instant.now()).isBefore(deadline);
			Thread.sleep(50);
			body = mockMvc.perform(bearer(get(path), host)).andReturn().getResponse().getContentAsString();
		} while ("IN_PROGRESS".equals(JsonPath.read(body, "$.analysis.status")));
		List<Integer> ids = JsonPath.read(body, "$.items[*].recommendationId");
		return new Fixture(host, guest, workspaceId, ids.stream().map(Integer::longValue).toList());
	}

	private String joinedMember(Fixture f, String email, String nickname) throws Exception {
		String token = signupLocal(email, "password1", nickname).accessToken();
		String invitation = mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(f.workspaceId())), f.host()))
			.andReturn().getResponse().getContentAsString();
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(JsonPath.<String>read(invitation, "$.token"))), token))
			.andExpect(status().isOk());
		return token;
	}

	private Long createWorkspace(String token, String name) throws Exception {
		String body = mockMvc.perform(json(bearer(post("/api/workspaces"), token), "{\"name\":\"%s\"}".formatted(name)))
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions add(String token, Long workspaceId, Long recommendationId) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/workspaces/%d/candidates".formatted(workspaceId)), token),
			"{\"recommendationId\":%d}".formatted(recommendationId)));
	}

	private static Long candidateIdOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.candidateId")).longValue();
	}

	private List<String> eventTypes() {
		return events.stream(WorkspaceEvent.class).map(WorkspaceEvent::type).toList();
	}

	private record Fixture(String host, String guest, Long workspaceId, List<Long> recommendationIds) {
	}
}
