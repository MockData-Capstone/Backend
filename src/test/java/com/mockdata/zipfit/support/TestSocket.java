package com.mockdata.zipfit.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 테스트용 WebSocket 클라이언트. 받은 메시지를 쌓아 두고 type으로 기다릴 수 있다. */
public class TestSocket extends TextWebSocketHandler {

	private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
	private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
	private WebSocketSession session;

	public static TestSocket connect(String url) throws Exception {
		TestSocket socket = new TestSocket();
		// 브라우저처럼 큰 메시지(SDP)도 받을 수 있게 클라이언트 버퍼를 늘린다 (기본 8KB).
		WebSocketContainer container = ContainerProvider.getWebSocketContainer();
		container.setDefaultMaxTextMessageBufferSize(64 * 1024);
		socket.session = new StandardWebSocketClient(container).execute(socket, url).get(5, TimeUnit.SECONDS);
		return socket;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		messages.add(message.getPayload());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		closed.complete(status);
	}

	public void send(String payload) throws Exception {
		session.sendMessage(new TextMessage(payload));
	}

	public void close() throws Exception {
		session.close();
	}

	/** 5초 안에 다음 메시지를 받는다. */
	public String next() throws InterruptedException {
		String message = messages.poll(5, TimeUnit.SECONDS);
		assertThat(message).as("5초 안에 메시지를 받아야 한다").isNotNull();
		return message;
	}

	/** 그 type의 메시지가 올 때까지 다른 메시지는 건너뛴다. */
	public String nextOfType(String type) throws InterruptedException {
		while (true) {
			String message = next();
			if (type.equals(JsonPath.read(message, "$.type"))) {
				return message;
			}
		}
	}

	public String poll(Duration timeout) throws InterruptedException {
		return messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
	}

	public int closeCode() throws Exception {
		return closed.get(5, TimeUnit.SECONDS).getCode();
	}
}
