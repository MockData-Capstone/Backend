package com.mockdata.zipfit.signaling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import com.mockdata.zipfit.support.TestSocket;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** SIG-01~05 시그널링 (방문자: 손님, 시청자: 호스트) */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalingIntegrationTest extends ScenarioTestSupport {

	@Value("${local.server.port}")
	int port;
	@MockitoSpyBean
	WorkspaceSessionRegistry sessionRegistry;

	String host;
	String guest;
	String third;
	Long workspaceId;
	Long hostId;
	Long guestId;
	Long tourId;

	@BeforeEach
	void setUp() throws Exception {
		doReturn(true).when(sessionRegistry).isConnected(anyLong(), anyLong());
		host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		workspaceId = createWorkspace(host, "시그널링방");
		guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		third = joinMember(host, workspaceId, "third@zipfit.com", "셋째");
		hostId = userIdOf("host@zipfit.com");
		guestId = userIdOf("guest@zipfit.com");

		String game = mockMvc.perform(json(bearer(post("/api/workspaces/%d/minigames".formatted(workspaceId)), host),
				"{\"type\":\"ROCK_PAPER_SCISSORS\",\"participantUserIds\":[%d,%d],\"visitorRule\":\"WINNER\"}"
					.formatted(hostId, guestId)))
			.andReturn().getResponse().getContentAsString();
		Long gameId = ((Number) JsonPath.read(game, "$.gameId")).longValue();
		mockMvc.perform(json(bearer(post("/api/minigames/%d/rounds/1/choices".formatted(gameId)), host), "{\"choice\":\"ROCK\"}"));
		mockMvc.perform(json(bearer(post("/api/minigames/%d/rounds/1/choices".formatted(gameId)), guest), "{\"choice\":\"PAPER\"}"));
		String tour = mockMvc.perform(bearer(post("/api/workspaces/%d/tours".formatted(workspaceId)), guest))
			.andReturn().getResponse().getContentAsString();
		tourId = ((Number) JsonPath.read(tour, "$.tour.tourId")).longValue();
		mockMvc.perform(bearer(post("/api/tours/%d/participants".formatted(tourId)), host));
	}

	@Test
	@DisplayName("SIG-01 참여자가 아니거나 주소가 잘못됐으면 사유를 담은 close code로 닫는다")
	void rejectsConnection() throws Exception {
		assertThat(connect(third).closeCode()).isEqualTo(4403);
		assertThat(TestSocket.connect(url("/ws/signaling?token=" + host)).closeCode()).isEqualTo(4400);
		assertThat(TestSocket.connect(url("/ws/signaling?tourId=9999&token=" + host)).closeCode()).isEqualTo(4404);
		assertThat(TestSocket.connect(url("/ws/signaling?tourId=" + tourId)).closeCode()).isEqualTo(4401);
	}

	@Test
	@DisplayName("SIG-01~03 JOIN하면 기존 참여자와 방문자를 받고, SDP·ICE는 지정한 상대에게만 간다")
	void joinAndRelay() throws Exception {
		TestSocket visitor = joined(guest);
		TestSocket viewer = connect(host);
		viewer.send("{\"type\":\"JOIN\"}");
		String joined = viewer.nextOfType("JOINED");
		assertThat(JsonPath.<Integer>read(joined, "$.data.visitorUserId")).isEqualTo(guestId.intValue());
		assertThat(JsonPath.<List<Integer>>read(joined, "$.data.peers[*].userId")).containsExactly(guestId.intValue());
		String peerJoined = visitor.nextOfType("PEER_JOINED");
		assertThat(JsonPath.<Integer>read(peerJoined, "$.data.userId")).isEqualTo(hostId.intValue());
		assertThat(JsonPath.<String>read(peerJoined, "$.data.role")).isEqualTo("VIEWER");

		visitor.send("{\"type\":\"OFFER\",\"data\":{\"targetUserId\":%d,\"sdp\":\"%s\"}}".formatted(hostId, "v=0".repeat(4000)));
		String offer = viewer.nextOfType("OFFER");
		assertThat(JsonPath.<Integer>read(offer, "$.data.fromUserId")).isEqualTo(guestId.intValue());
		assertThat(JsonPath.<String>read(offer, "$.data.sdp")).hasSize(12000);
		viewer.send("{\"type\":\"ANSWER\",\"data\":{\"targetUserId\":%d,\"sdp\":\"answer\"}}".formatted(guestId));
		assertThat(JsonPath.<String>read(visitor.nextOfType("ANSWER"), "$.data.sdp")).isEqualTo("answer");

		visitor.send("{\"type\":\"ICE_CANDIDATE\",\"data\":{\"targetUserId\":%d,\"candidate\":{}}}"
			.formatted(userIdOf("third@zipfit.com")));
		assertThat(JsonPath.<String>read(visitor.nextOfType("ERROR"), "$.data.code")).isEqualTo(ErrorCode.PEER_NOT_FOUND.name());
	}

	@Test
	@DisplayName("SIG-04 연결 상태를 보고하면 다른 참여자에게 알리고 투어 상태 조회에 반영한다")
	void connectionState() throws Exception {
		TestSocket visitor = joined(guest);
		TestSocket viewer = joined(host);

		visitor.send("{\"type\":\"CONNECTION_STATE\",\"data\":{\"state\":\"CONNECTED\"}}");
		String state = viewer.nextOfType("PEER_STATE");
		assertThat(JsonPath.<String>read(state, "$.data.state")).isEqualTo("CONNECTED");
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours/current".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.tour.visitorConnected").value(true));

		// 방문자 연결이 끊기면 나머지에게 알리고 "방문자 연결 끊김"이 된다.
		visitor.close();
		assertThat(JsonPath.<Integer>read(viewer.nextOfType("PEER_LEFT"), "$.data.userId")).isEqualTo(guestId.intValue());
		mockMvc.perform(bearer(get("/api/workspaces/%d/tours/current".formatted(workspaceId)), third))
			.andExpect(jsonPath("$.tour.visitorConnected").value(false));
	}

	@Test
	@DisplayName("시청자가 나가면 그 연결만 닫히고, 방문자가 종료하면 모두 TOUR_ENDED를 받고 닫힌다")
	void leaveAndEnd() throws Exception {
		TestSocket visitor = joined(guest);
		TestSocket viewer = joined(host);
		visitor.nextOfType("PEER_JOINED");

		mockMvc.perform(bearer(delete("/api/tours/%d/participants/me".formatted(tourId)), host));
		assertThat(viewer.closeCode()).isEqualTo(4403);
		assertThat(JsonPath.<Integer>read(visitor.nextOfType("PEER_LEFT"), "$.data.userId")).isEqualTo(hostId.intValue());

		mockMvc.perform(bearer(post("/api/tours/%d/participants".formatted(tourId)), host));
		TestSocket again = joined(host);
		mockMvc.perform(bearer(delete("/api/tours/" + tourId), guest));
		assertThat(JsonPath.<String>read(again.nextOfType("TOUR_ENDED"), "$.data.reason")).isEqualTo("VISITOR_ENDED");
		assertThat(again.closeCode()).isEqualTo(4404);
		assertThat(visitor.closeCode()).isEqualTo(4404);
	}

	@Test
	@DisplayName("같은 회원이 다시 접속하면 이전 연결을 닫고, JOIN 전에는 중계하지 않는다")
	void reconnect() throws Exception {
		TestSocket first = connect(host);
		first.send("{\"type\":\"OFFER\",\"data\":{\"targetUserId\":%d}}".formatted(guestId));
		assertThat(JsonPath.<String>read(first.nextOfType("ERROR"), "$.data.code")).isEqualTo(ErrorCode.NOT_JOINED.name());

		TestSocket second = connect(host);
		assertThat(first.closeCode()).isEqualTo(4409);
		second.send("{\"type\":\"JOIN\"}");
		assertThat(second.nextOfType("JOINED")).isNotNull();
		assertThat(second.poll(Duration.ofMillis(200))).isNull();
	}

	private TestSocket joined(String token) throws Exception {
		TestSocket socket = connect(token);
		socket.send("{\"type\":\"JOIN\"}");
		socket.nextOfType("JOINED");
		return socket;
	}

	private TestSocket connect(String token) throws Exception {
		return TestSocket.connect(url("/ws/signaling?tourId=%d&token=%s".formatted(tourId, token)));
	}

	private String url(String path) {
		return "ws://localhost:" + port + path;
	}
}
