package com.mockdata.zipfit.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.ai.AiClient;
import com.mockdata.zipfit.ai.AiServerException;
import com.mockdata.zipfit.auth.AuthTestSupport;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** REC-01·02 AI 분석 요청과 추천 위치 조회 (AI 서버 대신 FakeAiClient) */
@RecordApplicationEvents
class RecommendationIntegrationTest extends AuthTestSupport {

	@MockitoSpyBean
	AiClient aiClient;
	@Autowired
	ChatService chatService;
	@Autowired
	ApplicationEvents events;

	@Test
	@DisplayName("대화에서 조건을 추출하고 추천 위치를 만들어, 완료되면 조회할 수 있다")
	void analyze() throws Exception {
		Fixture f = workspaceWithConversation("성수 쪽 투룸 찾자. 보증금 1000만, 월세 60만까지. 오르막은 싫어");

		mockMvc.perform(bearer(post(f.path()), f.token()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.analysisId").exists());
		assertThat(events.stream(WorkspaceEvent.class).map(WorkspaceEvent::type))
			.contains(RecommendationService.RECOMMENDATION_STARTED);

		awaitFinished(f);
		mockMvc.perform(bearer(get(f.path()), f.token()))
			.andExpect(jsonPath("$.analysis.status").value("COMPLETED"))
			.andExpect(jsonPath("$.analysis.conditions.region").value("성수"))
			.andExpect(jsonPath("$.analysis.conditions.roomType").value("TWO_ROOM"))
			.andExpect(jsonPath("$.analysis.conditions.depositMax").value(1000))
			.andExpect(jsonPath("$.analysis.conditions.monthlyRentMax").value(60))
			.andExpect(jsonPath("$.analysis.conditions.avoidSlope").value(true))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].latitude").exists())
			.andExpect(jsonPath("$.items[0].reason").exists())
			.andExpect(jsonPath("$.items[0].addedAsCandidate").value(false));
	}

	@Test
	@DisplayName("분석한 적이 없으면 분석 null과 빈 목록, 대화 기록이 없으면 시작할 수 없다")
	void beforeAnalysis() throws Exception {
		String token = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(token);
		String path = "/api/workspaces/%d/recommendations".formatted(workspaceId);

		mockMvc.perform(bearer(get(path), token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.analysis").value((Object) null))
			.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(bearer(post(path), token))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.NO_CONVERSATION.name()));
	}

	@Test
	@DisplayName("분석 중이면 새로 시작할 수 없고, 재분석 중에도 이전 추천을 계속 보여준다")
	void blocksWhileInProgress() throws Exception {
		Fixture f = workspaceWithConversation("홍대 원룸 월세 50만");
		mockMvc.perform(bearer(post(f.path()), f.token())).andExpect(status().isAccepted());
		awaitFinished(f);

		CountDownLatch release = new CountDownLatch(1);
		doAnswer(invocation -> {
			release.await(5, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		}).when(aiClient).recommend(any());
		try {
			mockMvc.perform(bearer(post(f.path()), f.token())).andExpect(status().isAccepted());
			mockMvc.perform(bearer(post(f.path()), f.token()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorCode.ANALYSIS_IN_PROGRESS.name()));
			mockMvc.perform(bearer(get(f.path()), f.token()))
				.andExpect(jsonPath("$.analysis.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.items.length()").value(3));
		} finally {
			release.countDown();
		}
		awaitFinished(f);
	}

	@Test
	@DisplayName("조건을 하나도 찾지 못하면 대화 보충을, AI 서버가 실패하면 재요청을 안내한다")
	void failures() throws Exception {
		Fixture f = workspaceWithConversation("안녕 다들 잘 지내?");
		mockMvc.perform(bearer(post(f.path()), f.token())).andExpect(status().isAccepted());
		awaitFinished(f);
		mockMvc.perform(bearer(get(f.path()), f.token()))
			.andExpect(jsonPath("$.analysis.status").value("FAILED"))
			.andExpect(jsonPath("$.analysis.failure").value("NO_CONDITIONS"))
			.andExpect(jsonPath("$.analysis.failureMessage").exists())
			.andExpect(jsonPath("$.items.length()").value(0));

		doThrow(new AiServerException("down")).when(aiClient).recommend(any());
		mockMvc.perform(bearer(post(f.path()), f.token())).andExpect(status().isAccepted());
		awaitFinished(f);
		mockMvc.perform(bearer(get(f.path()), f.token()))
			.andExpect(jsonPath("$.analysis.status").value("FAILED"))
			.andExpect(jsonPath("$.analysis.failure").value("AI_ERROR"));
	}

	@Test
	@DisplayName("멤버가 아니면 요청·조회할 수 없다")
	void membersOnly() throws Exception {
		Fixture f = workspaceWithConversation("성수 투룸");
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();

		mockMvc.perform(bearer(post(f.path()), outsider)).andExpect(status().isForbidden());
		mockMvc.perform(bearer(get(f.path()), outsider)).andExpect(status().isForbidden());
	}

	private Fixture workspaceWithConversation(String message) throws Exception {
		String token = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(token);
		Long userId = userRepository.findByEmailAndPasswordIsNotNull("host@zipfit.com").orElseThrow().getId();
		chatService.send(workspaceId, userId, "c-1", message);
		return new Fixture(token, "/api/workspaces/%d/recommendations".formatted(workspaceId));
	}

	private Long createWorkspace(String token) throws Exception {
		String body = mockMvc.perform(json(bearer(post("/api/workspaces"), token), "{\"name\":\"추천방\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void awaitFinished(Fixture f) throws Exception {
		Instant deadline = Instant.now().plusSeconds(5);
		while (true) {
			String body = mockMvc.perform(bearer(get(f.path()), f.token())).andReturn().getResponse().getContentAsString();
			if (!"IN_PROGRESS".equals(JsonPath.read(body, "$.analysis.status"))) {
				return;
			}
			assertThat(Instant.now()).as("5초 안에 분석이 끝나야 한다").isBefore(deadline);
			Thread.sleep(50);
		}
	}

	private record Fixture(String token, String path) {
	}
}
