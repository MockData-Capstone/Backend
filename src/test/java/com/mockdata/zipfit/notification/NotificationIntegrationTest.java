package com.mockdata.zipfit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.notification.service.WebPushSender;
import com.mockdata.zipfit.notification.socket.NotificationSessionRegistry;
import com.mockdata.zipfit.support.ScenarioTestSupport;
import com.mockdata.zipfit.support.TestSocket;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/** NOTI-01~05 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationIntegrationTest extends ScenarioTestSupport {

	@Value("${local.server.port}")
	int port;
	@MockitoSpyBean
	WorkspaceSessionRegistry workspaceSessions;
	@MockitoSpyBean
	WebPushSender webPushSender;
	@Autowired
	NotificationSessionRegistry notificationSessions;

	@Test
	@DisplayName("새 멤버 입장은 기존 멤버에게만 알리고, 들어온 본인에게는 보내지 않는다")
	void memberJoined() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "알림방");
		String guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");

		notifications(host)
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("MEMBER_JOINED"))
			.andExpect(jsonPath("$.items[0].message").value("손님님이 들어왔습니다."))
			.andExpect(jsonPath("$.items[0].workspaceName").value("알림방"))
			.andExpect(jsonPath("$.items[0].read").value(false))
			.andExpect(jsonPath("$.unreadCount").value(1));
		notifications(guest).andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(bearer(get("/api/notifications/unread-count"), host))
			.andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	@DisplayName("새 메시지는 Workspace별로 읽지 않은 1건에 묶고, 그 Workspace를 보고 있는 멤버에게는 보내지 않는다")
	void chatMessages() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "채팅알림방");
		String guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		Long guestId = userIdOf("guest@zipfit.com");
		Long hostId = userIdOf("host@zipfit.com");
		mockMvc.perform(bearer(patch("/api/notifications/read-all"), host));

		chatService.send(workspaceId, guestId, "c-1", "안녕");
		chatService.send(workspaceId, guestId, "c-2", "성수 어때");
		String body = notifications(host).andReturn().getResponse().getContentAsString();
		List<String> types = JsonPath.read(body, "$.items[?(@.read == false)].type");
		assertThat(types).containsExactly("NEW_MESSAGE");
		assertThat(JsonPath.<List<Integer>>read(body, "$.items[?(@.type == 'NEW_MESSAGE')].count")).containsExactly(2);
		assertThat(JsonPath.<List<String>>read(body, "$.items[?(@.type == 'NEW_MESSAGE')].message"))
			.containsExactly("새 메시지가 2개 있습니다.");
		notifications(guest).andExpect(jsonPath("$.items.length()").value(0));

		// 채팅 화면(Workspace 소켓)을 보고 있으면 새 메시지 알림을 보내지 않는다.
		mockMvc.perform(bearer(patch("/api/notifications/read-all"), host)).andExpect(jsonPath("$.updatedCount").value(1));
		doReturn(true).when(workspaceSessions).isConnected(eq(workspaceId), eq(hostId));
		chatService.send(workspaceId, guestId, "c-3", "보고 있지?");
		mockMvc.perform(bearer(get("/api/notifications/unread-count"), host)).andExpect(jsonPath("$.unreadCount").value(0));
	}

	@Test
	@DisplayName("AI 분석 완료와 후보지 추가(투표 필요)를 알리고, 지워진 후보지의 알림은 링크를 비활성으로 표시한다")
	void analysisAndCandidate() throws Exception {
		Scenario s = analyzedWorkspace();
		notifications(s.guest()).andExpect(jsonPath("$.items[0].type").value("ANALYSIS_COMPLETED"));

		Long candidateId = addCandidates(s, 1).getFirst();
		notifications(s.guest())
			.andExpect(jsonPath("$.items[0].type").value("VOTE_NEEDED"))
			.andExpect(jsonPath("$.items[0].targetId").value(candidateId))
			.andExpect(jsonPath("$.items[0].linkActive").value(true));
		mockMvc.perform(bearer(delete("/api/candidates/" + candidateId), s.host()));
		notifications(s.guest()).andExpect(jsonPath("$.items[0].linkActive").value(false));
	}

	@Test
	@DisplayName("NOTI-02·03 최신순 커서로 넘기고, 읽음 처리는 본인 알림만 바꾸며, 나간 Workspace의 알림은 빠진다")
	void pagingAndRead() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "페이지방");
		String guest = joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		joinMember(host, workspaceId, "third@zipfit.com", "셋째");
		joinMember(host, workspaceId, "fourth@zipfit.com", "넷째");

		String first = mockMvc.perform(bearer(get("/api/notifications").param("size", "2"), host))
			.andExpect(jsonPath("$.items[0].message").value("넷째님이 들어왔습니다."))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn().getResponse().getContentAsString();
		mockMvc.perform(bearer(get("/api/notifications").param("size", "2").param("cursor", JsonPath.<String>read(first, "$.nextCursor")), host))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].message").value("손님님이 들어왔습니다."))
			.andExpect(jsonPath("$.hasNext").value(false));
		mockMvc.perform(bearer(get("/api/notifications").param("cursor", "bad"), host))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURSOR.name()));

		Integer notificationId = JsonPath.read(first, "$.items[0].notificationId");
		mockMvc.perform(bearer(patch("/api/notifications/%d/read".formatted(notificationId)), guest))
			.andExpect(status().isNotFound());
		mockMvc.perform(bearer(patch("/api/notifications/%d/read".formatted(notificationId)), host))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(2));
		mockMvc.perform(bearer(get("/api/notifications").param("unreadOnly", "true"), host))
			.andExpect(jsonPath("$.items.length()").value(2));
		mockMvc.perform(bearer(patch("/api/notifications/read-all"), host))
			.andExpect(jsonPath("$.updatedCount").value(2))
			.andExpect(jsonPath("$.unreadCount").value(0));

		// 나간 Workspace의 알림은 목록과 개수에서 빠진다.
		notifications(guest).andExpect(jsonPath("$.items.length()").value(2));
		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/me".formatted(workspaceId)), guest));
		notifications(guest)
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.unreadCount").value(0));
	}

	@Test
	@DisplayName("NOTI-01 알림 소켓에 접속해 있으면 바로 받고, 다른 탭에서 읽으면 NOTIFICATION_READ로 맞춘다")
	void socketDelivery() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long workspaceId = createWorkspace(host, "소켓알림방");
		TestSocket socket = TestSocket.connect("ws://localhost:%d/ws/notifications?token=%s".formatted(port, host));
		assertThat(TestSocket.connect("ws://localhost:%d/ws/notifications".formatted(port)).closeCode()).isEqualTo(4401);
		awaitConnected(userIdOf("host@zipfit.com"));

		joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		String created = socket.nextOfType("NOTIFICATION_CREATED");
		assertThat(JsonPath.<String>read(created, "$.data.notification.type")).isEqualTo("MEMBER_JOINED");
		assertThat(JsonPath.<Integer>read(created, "$.data.unreadCount")).isEqualTo(1);

		Integer id = JsonPath.read(created, "$.data.notification.notificationId");
		mockMvc.perform(bearer(patch("/api/notifications/%d/read".formatted(id)), host));
		String read = socket.nextOfType("NOTIFICATION_READ");
		assertThat(JsonPath.<List<Integer>>read(read, "$.data.notificationIds")).containsExactly(id);
		assertThat(JsonPath.<Integer>read(read, "$.data.unreadCount")).isZero();
		socket.close();
	}

	@Test
	@DisplayName("NOTI-04·05 구독은 endpoint 기준으로 갱신·이전되고, 접속 중이 아닌 회원에게는 푸시로 보낸다")
	void pushSubscriptions() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String other = signupLocal("other@zipfit.com", "password1", "다른회원").accessToken();
		mockMvc.perform(bearer(get("/api/push-subscriptions/vapid-public-key"), host))
			.andExpect(jsonPath("$.enabled").value(false));

		subscribe(other, "https://push.example/abc").andExpect(status().isCreated());
		subscribe(host, "https://push.example/abc").andExpect(status().isCreated());
		mockMvc.perform(bearer(delete("/api/push-subscriptions").param("endpoint", "https://push.example/abc"), other))
			.andExpect(status().isNotFound());

		Long workspaceId = createWorkspace(host, "푸시방");
		joinMember(host, workspaceId, "guest@zipfit.com", "손님");
		verify(webPushSender).send(any(), any());

		mockMvc.perform(bearer(delete("/api/push-subscriptions").param("endpoint", "https://push.example/abc"), host))
			.andExpect(status().isNoContent());
		subscribe(host, " ").andExpect(status().isBadRequest());
	}

	private ResultActions notifications(String token) throws Exception {
		return mockMvc.perform(bearer(get("/api/notifications"), token)).andExpect(status().isOk());
	}

	private ResultActions subscribe(String token, String endpoint) throws Exception {
		return mockMvc.perform(json(bearer(post("/api/push-subscriptions"), token),
			"{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"key\",\"auth\":\"auth\"}}".formatted(endpoint)));
	}

	private void awaitConnected(Long userId) throws InterruptedException {
		for (int i = 0; i < 100 && !notificationSessions.isConnected(userId); i++) {
			Thread.sleep(20);
		}
	}
}
