package com.mockdata.zipfit.minigame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.minigame.dto.GameEvents;
import com.mockdata.zipfit.minigame.service.MiniGameScheduler;
import com.mockdata.zipfit.minigame.service.MiniGameService;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** GAME-01~06 */
@RecordApplicationEvents
class MiniGameIntegrationTest extends ScenarioTestSupport {

	@MockitoSpyBean
	WorkspaceSessionRegistry sessionRegistry;
	@Autowired
	MiniGameService gameService;
	@Autowired
	MiniGameScheduler scheduler;
	@Autowired
	ApplicationEvents events;

	String host;
	String guest;
	String third;
	Long workspaceId;
	Long hostId;
	Long guestId;
	Long thirdId;

	@BeforeEach
	void setUp() throws Exception {
		// 소켓을 열지 않아도 모든 멤버가 접속 중인 것으로 본다.
		doReturn(true).when(sessionRegistry).isConnected(anyLong(), anyLong());
		host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		workspaceId = createWorkspace(host, "게임방");
		guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		third = joinMember(host, workspaceId, "third@zipfit.com", "셋째");
		hostId = userIdOf("host@zipfit.com");
		guestId = userIdOf("guest@zipfit.com");
		thirdId = userIdOf("third@zipfit.com");
	}

	@Test
	@DisplayName("룰렛: 추첨하면 서버가 방문자를 정하고, 끝난 게임은 이력으로 남는다")
	void roulette() throws Exception {
		Long gameId = start(host, "ROULETTE", List.of(hostId, guestId), null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.visitor").doesNotExist())
			.andExpect(jsonPath("$.participants.length()").value(2))
			.andReturn().getResponse().getContentAsString().transform(this::gameIdOf);
		mockMvc.perform(bearer(get("/api/workspaces/%d/minigames/current".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.game.gameId").value(gameId));

		mockMvc.perform(bearer(post("/api/minigames/%d/draw".formatted(gameId)), third))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_GAME_PLAYER.name()));
		String finished = mockMvc.perform(bearer(post("/api/minigames/%d/draw".formatted(gameId)), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(finished, "$.visitor.userId").longValue()).isIn(hostId, guestId);
		mockMvc.perform(bearer(post("/api/minigames/%d/draw".formatted(gameId)), guest))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.GAME_NOT_IN_PROGRESS.name()));

		mockMvc.perform(bearer(get("/api/workspaces/%d/minigames/current".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.game").value((Object) null));
		mockMvc.perform(bearer(get("/api/workspaces/%d/minigames".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].visitor.nickname").exists());
		assertThat(eventTypes()).contains(GameEvents.GAME_STARTED, GameEvents.GAME_FINISHED);
	}

	@Test
	@DisplayName("참여 멤버가 2명 미만이거나 접속 중이 아니거나, 진행 중인 게임이 있으면 시작할 수 없다")
	void startRejected() throws Exception {
		start(host, "ROCK_PAPER_SCISSORS", List.of(hostId, guestId), null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.VISITOR_RULE_REQUIRED.name()));
		start(host, "ROULETTE", List.of(hostId, hostId), null)
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_ENOUGH_PLAYERS.name()));
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		start(host, "ROULETTE", List.of(hostId, userIdOf("out@zipfit.com")), null)
			.andExpect(jsonPath("$.code").value(ErrorCode.PLAYER_NOT_AVAILABLE.name()));
		doReturn(false).when(sessionRegistry).isConnected(workspaceId, guestId);
		start(host, "ROULETTE", List.of(hostId, guestId), null)
			.andExpect(jsonPath("$.code").value(ErrorCode.PLAYER_NOT_AVAILABLE.name()));

		start(host, "ROULETTE", List.of(hostId, thirdId), null).andExpect(status().isCreated());
		start(third, "ROULETTE", List.of(hostId, thirdId), null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.GAME_IN_PROGRESS.name()));
		start(outsider, "ROULETTE", List.of(hostId, thirdId), null).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("가위바위보(패자가 방문자): 모두 내면 판정해 진 쪽만 남기고, 1명이 남으면 방문자가 된다")
	void rockPaperScissorsLoser() throws Exception {
		Long gameId = gameIdOf(start(host, "ROCK_PAPER_SCISSORS", List.of(hostId, guestId, thirdId), "LOSER")
			.andExpect(jsonPath("$.currentRound").value(1))
			.andExpect(jsonPath("$.roundDeadline").exists())
			.andReturn().getResponse().getContentAsString());

		choose(host, gameId, 1, "ROCK").andExpect(status().isOk());
		choose(guest, gameId, 1, "ROCK")
			.andExpect(jsonPath("$.submittedUserIds.length()").value(2))
			.andExpect(jsonPath("$.rounds.length()").value(0));
		choose(guest, gameId, 1, "PAPER")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_SUBMITTED.name()));
		choose(third, gameId, 2, "PAPER")
			.andExpect(jsonPath("$.code").value(ErrorCode.ROUND_CLOSED.name()));

		// 바위가 이겼고 패자가 방문자이므로 가위를 낸 셋째만 남는다.
		choose(third, gameId, 1, "SCISSORS")
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.visitor.userId").value(thirdId))
			.andExpect(jsonPath("$.rounds[0].draw").value(false))
			.andExpect(jsonPath("$.rounds[0].choices.length()").value(3))
			.andExpect(jsonPath("$.rounds[0].eliminatedUserIds.length()").value(2));
		assertThat(eventTypes()).contains(GameEvents.ROUND_OPENED, GameEvents.ROUND_RESULT, GameEvents.GAME_FINISHED);
	}

	@Test
	@DisplayName("가위바위보(승자가 방문자): 모두 같은 걸 내면 무승부로 같은 멤버가 다시 한다")
	void rockPaperScissorsDraw() throws Exception {
		Long gameId = gameIdOf(start(host, "ROCK_PAPER_SCISSORS", List.of(hostId, guestId), "WINNER")
			.andReturn().getResponse().getContentAsString());

		choose(host, gameId, 1, "ROCK");
		choose(guest, gameId, 1, "ROCK")
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.currentRound").value(2))
			.andExpect(jsonPath("$.rounds[0].draw").value(true));
		choose(host, gameId, 2, "PAPER");
		choose(guest, gameId, 2, "ROCK")
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.visitor.userId").value(hostId));
	}

	@Test
	@DisplayName("라운드 제한 시간이 지나면 내지 않은 참가자 대신 서버가 무작위로 고른다")
	void roundTimeout() throws Exception {
		Long gameId = gameIdOf(start(host, "ROCK_PAPER_SCISSORS", List.of(hostId, guestId), "WINNER")
			.andReturn().getResponse().getContentAsString());
		choose(host, gameId, 1, "ROCK");

		gameService.closeRoundIfExpired(gameId, Instant.now().plus(Duration.ofMinutes(1)));

		String body = mockMvc.perform(bearer(get("/api/minigames/" + gameId), host)).andReturn().getResponse().getContentAsString();
		List<Boolean> autos = JsonPath.read(body, "$.rounds[0].choices[?(@.userId == %d)].auto".formatted(guestId));
		assertThat(autos).containsExactly(true);
	}

	@Test
	@DisplayName("GAME-04 시작한 멤버만 중단할 수 있고, 중단한 게임은 다시 중단할 수 없다")
	void abort() throws Exception {
		Long gameId = gameIdOf(start(host, "ROULETTE", List.of(hostId, guestId), null)
			.andReturn().getResponse().getContentAsString());

		mockMvc.perform(bearer(delete("/api/minigames/" + gameId), guest))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_GAME_STARTER.name()));
		mockMvc.perform(bearer(delete("/api/minigames/" + gameId), host))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ABORTED"))
			.andExpect(jsonPath("$.abortReason").value("STOPPED_BY_STARTER"));
		mockMvc.perform(bearer(delete("/api/minigames/" + gameId), host))
			.andExpect(status().isConflict());
		start(guest, "ROULETTE", List.of(hostId, guestId), null).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("참가자가 나가 2명 미만이 되면 자동으로 중단하고, 연결이 끊긴 채 유예 시간이 지나면 결과에서 뺀다")
	void playersDropOut() throws Exception {
		Long first = gameIdOf(start(host, "ROULETTE", List.of(hostId, guestId), null)
			.andReturn().getResponse().getContentAsString());
		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(workspaceId)), guest))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(get("/api/minigames/" + first), host))
			.andExpect(jsonPath("$.status").value("ABORTED"))
			.andExpect(jsonPath("$.abortReason").value("NOT_ENOUGH_PLAYERS"))
			.andExpect(jsonPath("$.participants[1].status").value("LEFT"));
		assertThat(eventTypes()).contains(GameEvents.GAME_PLAYER_LEFT, GameEvents.GAME_ABORTED);

		String fourth = joinMember(host, workspaceId, "fourth@zipfit.com", "넷째");
		Long second = gameIdOf(start(host, "ROULETTE", List.of(hostId, thirdId, userIdOf("fourth@zipfit.com")), null)
			.andReturn().getResponse().getContentAsString());
		doReturn(false).when(sessionRegistry).isConnected(eq(workspaceId), eq(thirdId));
		Instant now = Instant.now();
		scheduler.check(now);
		mockMvc.perform(bearer(get("/api/minigames/" + second), fourth))
			.andExpect(jsonPath("$.participants[1].status").value("PLAYING"));
		scheduler.check(now.plusSeconds(11));
		mockMvc.perform(bearer(get("/api/minigames/" + second), fourth))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.participants[1].status").value("LEFT"));
	}

	private ResultActions start(String token, String type, List<Long> participants, String visitorRule) throws Exception {
		String rule = visitorRule == null ? "null" : "\"" + visitorRule + "\"";
		return mockMvc.perform(json(bearer(post("/api/workspaces/%d/minigames".formatted(workspaceId)), token),
			"{\"type\":\"%s\",\"participantUserIds\":%s,\"visitorRule\":%s}".formatted(type, participants, rule)));
	}

	private ResultActions choose(String token, Long gameId, int roundNo, String choice) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/minigames/%d/rounds/%d/choices".formatted(gameId, roundNo)), token),
			"{\"choice\":\"%s\"}".formatted(choice)));
	}

	private Long gameIdOf(String body) {
		return ((Number) JsonPath.read(body, "$.gameId")).longValue();
	}

	private List<String> eventTypes() {
		return events.stream(WorkspaceEvent.class).map(WorkspaceEvent::type).toList();
	}
}
