package com.mockdata.zipfit.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.mockdata.zipfit.support.ScenarioTestSupport;
import com.mockdata.zipfit.tour.entity.TourEnums.ConnectionState;
import com.mockdata.zipfit.tour.service.TourEvent;
import com.mockdata.zipfit.tour.service.TourScheduler;
import com.mockdata.zipfit.tour.service.TourService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

/** TOUR-01~06 (최대 인원을 2명으로 줄이고 TURN을 설정해 시험한다) */
@RecordApplicationEvents
@TestPropertySource(properties = {
	"tour.max-participants=2",
	"webrtc.turn-urls=turn:turn.zipfit.test:3478",
	"webrtc.turn-secret=test-turn-secret"
})
class TourIntegrationTest extends ScenarioTestSupport {

	@MockitoSpyBean
	WorkspaceSessionRegistry sessionRegistry;
	@Autowired
	TourService tourService;
	@Autowired
	TourScheduler tourScheduler;
	@Autowired
	ApplicationEvents events;

	String host;
	String guest;
	String third;
	Long workspaceId;
	Long guestId;

	/** 손님이 가위바위보(승자가 방문자)에서 이겨 방문자가 된 Workspace */
	@BeforeEach
	void setUp() throws Exception {
		doReturn(true).when(sessionRegistry).isConnected(anyLong(), anyLong());
		host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		workspaceId = createWorkspace(host, "투어방");
		guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		third = joinMember(host, workspaceId, "third@zipfit.com", "셋째");
		guestId = userIdOf("guest@zipfit.com");
		String game = mockMvc.perform(json(bearer(post("/api/workspaces/%d/minigames".formatted(workspaceId)), host),
				"{\"type\":\"ROCK_PAPER_SCISSORS\",\"participantUserIds\":[%d,%d],\"visitorRule\":\"WINNER\"}"
					.formatted(userIdOf("host@zipfit.com"), guestId)))
			.andReturn().getResponse().getContentAsString();
		Long gameId = ((Number) JsonPath.read(game, "$.gameId")).longValue();
		choose(host, gameId, "ROCK");
		choose(guest, gameId, "PAPER").andExpect(jsonPath("$.visitor.userId").value(guestId));
	}

	@Test
	@DisplayName("TOUR-01 가장 최근 미니게임의 방문자만 시작하고, 시그널링 주소와 ICE 서버를 받는다")
	void start() throws Exception {
		startTour(host)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_SELECTED_VISITOR.name()));

		Long tourId = tourIdOf(startTour(guest)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tour.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.tour.visitor.nickname").value("손님"))
			.andExpect(jsonPath("$.tour.participants[0].role").value("VISITOR"))
			.andExpect(jsonPath("$.tour.maxParticipants").value(2))
			.andExpect(jsonPath("$.signalingPath").exists())
			.andExpect(jsonPath("$.ice.iceServers[0].urls[0]").value("stun:stun.l.google.com:19302")));
		startTour(guest)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.TOUR_IN_PROGRESS.name()));

		mockMvc.perform(bearer(get("/api/workspaces/%d/tours/current".formatted(workspaceId)), host))
			.andExpect(jsonPath("$.tour.tourId").value(tourId))
			.andExpect(jsonPath("$.tour.visitorConnected").value(false));
		assertThat(events.stream(WorkspaceEvent.class).map(WorkspaceEvent::type)).contains(TourService.TOUR_STARTED);
	}

	@Test
	@DisplayName("TOUR-02·04 시청자는 최대 인원까지 참여하고, 나가면 본인 연결만 끝난다")
	void joinAndLeave() throws Exception {
		Long tourId = tourIdOf(startTour(guest));

		join(host, tourId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tour.participantCount").value(2))
			.andExpect(jsonPath("$.tour.participants[1].role").value("VIEWER"));
		join(host, tourId).andExpect(jsonPath("$.tour.participantCount").value(2));
		join(third, tourId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.TOUR_FULL.name()));

		mockMvc.perform(bearer(delete("/api/tours/%d/participants/me".formatted(tourId)), guest))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.VISITOR_CANNOT_LEAVE.name()));
		mockMvc.perform(bearer(delete("/api/tours/%d/participants/me".formatted(tourId)), host))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(delete("/api/tours/%d/participants/me".formatted(tourId)), host))
			.andExpect(status().isForbidden());
		assertThat(events.stream(TourEvent.class).map(TourEvent::type)).contains(TourEvent.PARTICIPANT_REMOVED);

		// 자리가 나면 다른 멤버가 들어올 수 있다.
		join(third, tourId).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("TOUR-02 TURN 자격증명은 참여자에게만, 만료 시각과 회원 ID로 만든 일회성 값으로 준다")
	void iceServers() throws Exception {
		Long tourId = tourIdOf(startTour(guest));

		String body = mockMvc.perform(bearer(get("/api/tours/%d/ice-servers".formatted(tourId)), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.iceServers[1].urls[0]").value("turn:turn.zipfit.test:3478"))
			.andReturn().getResponse().getContentAsString();
		String username = JsonPath.read(body, "$.iceServers[1].username");
		assertThat(username).matches("\\d+:" + guestId);
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec("test-turn-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		assertThat(JsonPath.<String>read(body, "$.iceServers[1].credential"))
			.isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8))));

		mockMvc.perform(bearer(get("/api/tours/%d/ice-servers".formatted(tourId)), host))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_TOUR_PARTICIPANT.name()));
	}

	@Test
	@DisplayName("TOUR-04·06 방문자만 종료할 수 있고, 종료된 투어는 이력에 남으며 같은 게임 결과로 다시 시작할 수 없다")
	void end() throws Exception {
		Long tourId = tourIdOf(startTour(guest));
		join(host, tourId);

		mockMvc.perform(bearer(delete("/api/tours/" + tourId), host))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value(ErrorCode.ONLY_VISITOR_ALLOWED.name()));
		mockMvc.perform(bearer(delete("/api/tours/" + tourId), guest)).andExpect(status().isNoContent());

		mockMvc.perform(bearer(get("/api/workspaces/%d/tours/current".formatted(workspaceId)), host))
			.andExpect(jsonPath("$.tour").value((Object) null));
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].endReason").value("VISITOR_ENDED"))
			.andExpect(jsonPath("$.items[0].endedAt").exists());
		join(third, tourId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.TOUR_NOT_IN_PROGRESS.name()));
		startTour(guest)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(ErrorCode.TOUR_ALREADY_HELD.name()));
		assertThat(events.stream(TourEvent.class).map(TourEvent::type)).contains(TourEvent.TOUR_ENDED);
	}

	@Test
	@DisplayName("TOUR-05 끊긴 방문자는 재참여할 수 있고, 대기 시간 안에 돌아오지 않으면 투어가 끝난다")
	void visitorRejoinAndTimeout() throws Exception {
		Long tourId = tourIdOf(startTour(guest));
		tourService.reportConnectionState(tourId, guestId, ConnectionState.DISCONNECTED);
		mockMvc.perform(bearer(post("/api/tours/%d/rejoin".formatted(tourId)), host))
			.andExpect(status().isForbidden());
		mockMvc.perform(bearer(post("/api/tours/%d/rejoin".formatted(tourId)), guest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tour.participants[0].connectionState").value("CONNECTING"));
		tourScheduler.check(Instant.now().plus(Duration.ofMinutes(4)));
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours/current".formatted(workspaceId)), host))
			.andExpect(jsonPath("$.tour.status").value("IN_PROGRESS"));

		tourService.reportConnectionState(tourId, guestId, ConnectionState.DISCONNECTED);
		tourScheduler.check(Instant.now().plus(Duration.ofMinutes(4)));
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours".formatted(workspaceId)), host))
			.andExpect(jsonPath("$.items[0].endReason").value("VISITOR_TIMEOUT"));
	}

	@Test
	@DisplayName("방문자가 내보내지면 투어가 끝나고, Workspace를 삭제하면 투어도 지워진다")
	void visitorRemoved() throws Exception {
		Long tourId = tourIdOf(startTour(guest));
		join(host, tourId);

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/%d".formatted(workspaceId, guestId)), host))
			.andExpect(status().isNoContent());
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours".formatted(workspaceId)), host))
			.andExpect(jsonPath("$.items[0].endReason").value("VISITOR_LEFT_WORKSPACE"));

		mockMvc.perform(bearer(delete("/api/workspaces/" + workspaceId), host)).andExpect(status().isNoContent());
	}

	private ResultActions startTour(String token) throws Exception {
		return mockMvc.perform(bearer(post("/api/workspaces/%d/tours".formatted(workspaceId)), token));
	}

	private ResultActions join(String token, Long tourId) throws Exception {
		return mockMvc.perform(bearer(post("/api/tours/%d/participants".formatted(tourId)), token));
	}

	private ResultActions choose(String token, Long gameId, String hand) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/minigames/%d/rounds/1/choices".formatted(gameId)), token),
			"{\"choice\":\"%s\"}".formatted(hand)));
	}

	private static Long tourIdOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.tour.tourId")).longValue();
	}
}
