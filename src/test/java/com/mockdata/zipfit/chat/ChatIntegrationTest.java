package com.mockdata.zipfit.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mockdata.zipfit.auth.AuthTestSupport;
import com.mockdata.zipfit.chat.repository.ChatMessageRepository;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** CHAT-01·02 Workspace 소켓과 대화 기록 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatIntegrationTest extends AuthTestSupport {

	@Value("${local.server.port}")
	int port;
	@Autowired
	ChatService chatService;
	@Autowired
	ChatMessageRepository messageRepository;
	@Autowired
	WorkspaceSessionRegistry registry;

	@Test
	@DisplayName("토큰이 없거나 멤버가 아니거나 Workspace가 없으면 사유를 담은 close code로 닫는다")
	void rejectsConnection() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		Long id = createWorkspace(host, "채팅방");

		assertThat(connect("/ws/workspaces?workspaceId=" + id).closeCode()).isEqualTo(4401);
		assertThat(connect("/ws/workspaces?workspaceId=%d&token=%s".formatted(id, outsider)).closeCode()).isEqualTo(4403);
		assertThat(connect("/ws/workspaces?workspaceId=9999&token=" + host).closeCode()).isEqualTo(4404);
		assertThat(connect("/ws/workspaces?token=" + host).closeCode()).isEqualTo(4400);
	}

	@Test
	@DisplayName("CHAT_SEND를 저장해 멤버 전원에게 CHAT_MESSAGE로 보내고, 같은 clientMessageId는 다시 저장하지 않는다")
	void sendMessage() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "채팅방");
		join(guest, issueInvitation(host, id));
		TestSocket hostSocket = connectWorkspace(id, host, "host@zipfit.com");
		TestSocket guestSocket = connectWorkspace(id, guest, "guest@zipfit.com");

		hostSocket.send("{\"type\":\"CHAT_SEND\",\"data\":{\"clientMessageId\":\"c-1\",\"content\":\"안녕\"}}");

		String received = guestSocket.next();
		assertThat(JsonPath.<String>read(received, "$.type")).isEqualTo("CHAT_MESSAGE");
		assertThat(JsonPath.<String>read(received, "$.data.content")).isEqualTo("안녕");
		assertThat(JsonPath.<String>read(received, "$.data.sender.nickname")).isEqualTo("호스트");
		assertThat(JsonPath.<String>read(received, "$.sentAt")).isNotBlank();
		String echoed = hostSocket.next();
		assertThat(JsonPath.<String>read(echoed, "$.data.clientMessageId")).isEqualTo("c-1");
		Integer messageId = JsonPath.read(echoed, "$.data.messageId");

		// 응답을 못 받아 다시 보내도 새로 저장하지 않고, 보낸 연결에만 확정 메시지를 준다.
		hostSocket.send("{\"type\":\"CHAT_SEND\",\"data\":{\"clientMessageId\":\"c-1\",\"content\":\"안녕\"}}");
		assertThat(JsonPath.<Integer>read(hostSocket.next(), "$.data.messageId")).isEqualTo(messageId);
		assertThat(guestSocket.poll(Duration.ofMillis(300))).isNull();
		assertThat(messageRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("빈 메시지, 1,000자 초과, 알 수 없는 type은 보낸 연결에만 ERROR로 알린다")
	void rejectsInvalidMessages() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		Long id = createWorkspace(host, "채팅방");
		TestSocket socket = connectWorkspace(id, host, "host@zipfit.com");

		socket.send("{\"type\":\"CHAT_SEND\",\"data\":{\"clientMessageId\":\"c-1\",\"content\":\"   \"}}");
		String error = socket.next();
		assertThat(JsonPath.<String>read(error, "$.type")).isEqualTo("ERROR");
		assertThat(JsonPath.<String>read(error, "$.data.code")).isEqualTo(ErrorCode.EMPTY_MESSAGE.name());
		assertThat(JsonPath.<String>read(error, "$.data.requestId")).isEqualTo("c-1");

		socket.send("{\"type\":\"CHAT_SEND\",\"data\":{\"clientMessageId\":\"c-2\",\"content\":\"%s\"}}".formatted("가".repeat(1001)));
		assertThat(JsonPath.<String>read(socket.next(), "$.data.code")).isEqualTo(ErrorCode.MESSAGE_TOO_LONG.name());
		socket.send("{\"type\":\"HELLO\"}");
		assertThat(JsonPath.<String>read(socket.next(), "$.data.code")).isEqualTo(ErrorCode.UNSUPPORTED_SOCKET_MESSAGE.name());
		socket.send("not json");
		assertThat(JsonPath.<String>read(socket.next(), "$.data.code")).isEqualTo(ErrorCode.INVALID_SOCKET_MESSAGE.name());
		assertThat(messageRepository.count()).isZero();
	}

	@Test
	@DisplayName("내보내진 멤버는 MEMBER_KICKED를 받은 뒤 연결이 닫히고, Workspace를 삭제하면 모두 닫힌다")
	void kickAndDeleteCloseSockets() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "채팅방");
		join(guest, issueInvitation(host, id));
		TestSocket hostSocket = connectWorkspace(id, host, "host@zipfit.com");
		TestSocket guestSocket = connectWorkspace(id, guest, "guest@zipfit.com");
		Long guestId = userRepository.findByEmailAndPasswordIsNotNull("guest@zipfit.com").orElseThrow().getId();

		mockMvc.perform(bearer(delete("/api/workspaces/%d/members/%d".formatted(id, guestId)), host))
			.andExpect(status().isNoContent());
		assertThat(JsonPath.<String>read(guestSocket.next(), "$.type")).isEqualTo("MEMBER_KICKED");
		assertThat(guestSocket.closeCode()).isEqualTo(4403);
		assertThat(JsonPath.<Integer>read(hostSocket.next(), "$.data.userId")).isEqualTo(guestId.intValue());

		mockMvc.perform(bearer(delete("/api/workspaces/" + id), host)).andExpect(status().isNoContent());
		assertThat(JsonPath.<String>read(hostSocket.next(), "$.type")).isEqualTo("WORKSPACE_DELETED");
		assertThat(hostSocket.closeCode()).isEqualTo(4404);
	}

	@Test
	@DisplayName("CHAT-02 최신 기록부터 시간순 페이지로 받고, before로 이전 기록을, after로 놓친 메시지를 받는다")
	void history() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String outsider = signupLocal("out@zipfit.com", "password1", "외부인").accessToken();
		Long id = createWorkspace(host, "채팅방");
		Long hostId = userRepository.findByEmailAndPasswordIsNotNull("host@zipfit.com").orElseThrow().getId();
		for (int i = 1; i <= 5; i++) {
			chatService.send(id, hostId, "c-" + i, "메시지" + i);
		}

		String latest = mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)).param("size", "2"), host))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].content").value("메시지4"))
			.andExpect(jsonPath("$.items[1].content").value("메시지5"))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andReturn().getResponse().getContentAsString();
		String cursor = JsonPath.read(latest, "$.nextCursor");

		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)).param("before", cursor).param("size", "10"), host))
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].content").value("메시지1"))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.nextCursor").doesNotExist());

		Integer firstId = JsonPath.read(latest, "$.items[0].messageId");
		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)).param("after", firstId.toString()), host))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].content").value("메시지5"));

		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)).param("before", "3").param("after", "1"), host))
			.andExpect(status().isBadRequest());
		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)).param("size", "101"), host))
			.andExpect(status().isBadRequest());
		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)), outsider))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("AUTH-06 탈퇴한 회원의 메시지는 작성자와 내용을 지우고 deleted로 남는다")
	void withdrawnSenderMessage() throws Exception {
		String host = signupLocal("host@zipfit.com", "password1", "호스트").accessToken();
		String guest = signupLocal("guest@zipfit.com", "password1", "손님").accessToken();
		Long id = createWorkspace(host, "채팅방");
		join(guest, issueInvitation(host, id));
		Long guestId = userRepository.findByEmailAndPasswordIsNotNull("guest@zipfit.com").orElseThrow().getId();
		chatService.send(id, guestId, "c-1", "곧 탈퇴할게요");

		mockMvc.perform(bearer(delete("/api/users/me"), guest)).andExpect(status().isNoContent());

		mockMvc.perform(bearer(get("/api/workspaces/%d/messages".formatted(id)), host))
			.andExpect(jsonPath("$.items[0].deleted").value(true))
			.andExpect(jsonPath("$.items[0].content").doesNotExist())
			.andExpect(jsonPath("$.items[0].sender").doesNotExist());
	}

	private Long createWorkspace(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(json(bearer(post("/api/workspaces"), accessToken), "{\"name\":\"%s\"}".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String issueInvitation(String accessToken, Long workspaceId) throws Exception {
		String body = mockMvc.perform(bearer(post("/api/workspaces/%d/invitations".formatted(workspaceId)), accessToken))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private void join(String accessToken, String token) throws Exception {
		mockMvc.perform(bearer(post("/api/invitations/%s/accept".formatted(token)), accessToken))
			.andExpect(status().isOk());
	}

	/** 연결한 뒤 서버에 등록될 때까지 기다린다. */
	private TestSocket connectWorkspace(Long workspaceId, String accessToken, String email) throws Exception {
		TestSocket socket = connect("/ws/workspaces?workspaceId=%d&token=%s".formatted(workspaceId, accessToken));
		Long userId = userRepository.findByEmailAndPasswordIsNotNull(email).orElseThrow().getId();
		Instant deadline = Instant.now().plusSeconds(5);
		while (!registry.isConnected(workspaceId, userId)) {
			assertThat(Instant.now()).isBefore(deadline);
			Thread.onSpinWait();
		}
		return socket;
	}

	private TestSocket connect(String path) throws Exception {
		TestSocket socket = new TestSocket();
		socket.session = new StandardWebSocketClient()
			.execute(socket, "ws://localhost:" + port + path)
			.get(5, TimeUnit.SECONDS);
		return socket;
	}

	static class TestSocket extends TextWebSocketHandler {

		final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
		final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
		WebSocketSession session;

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			messages.add(message.getPayload());
		}

		@Override
		public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
			closed.complete(status);
		}

		void send(String payload) throws Exception {
			session.sendMessage(new TextMessage(payload));
		}

		/** 연결·해제 때마다 오가는 MEMBER_ONLINE/OFFLINE은 건너뛰고, 테스트가 보려는 메시지를 돌려준다. */
		String next() throws InterruptedException {
			Instant deadline = Instant.now().plusSeconds(5);
			String message;
			String type;
			do {
				message = poll(Duration.between(Instant.now(), deadline));
				assertThat(message).as("5초 안에 메시지를 받아야 한다").isNotNull();
				type = JsonPath.read(message, "$.type");
			} while ("MEMBER_ONLINE".equals(type) || "MEMBER_OFFLINE".equals(type));
			return message;
		}

		String poll(Duration timeout) throws InterruptedException {
			return messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}

		int closeCode() throws Exception {
			return closed.get(5, TimeUnit.SECONDS).getCode();
		}
	}
}
