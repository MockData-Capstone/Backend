package com.mockdata.zipfit.common.config;

import com.mockdata.zipfit.chat.socket.WorkspaceSocketHandler;
import com.mockdata.zipfit.common.websocket.SocketAuthInterceptor;
import com.mockdata.zipfit.notification.socket.NotificationSocketHandler;
import com.mockdata.zipfit.signaling.SignalingSocketHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 접속 주소에 ?token={accessToken}을 붙여 인증한다 ({@link SocketAuthInterceptor}). */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final WorkspaceSocketHandler workspaceSocketHandler;
	private final SignalingSocketHandler signalingSocketHandler;
	private final NotificationSocketHandler notificationSocketHandler;
	private final SocketAuthInterceptor socketAuthInterceptor;
	private final List<String> allowedOrigins;

	public WebSocketConfig(
		WorkspaceSocketHandler workspaceSocketHandler,
		SignalingSocketHandler signalingSocketHandler,
		NotificationSocketHandler notificationSocketHandler,
		SocketAuthInterceptor socketAuthInterceptor,
		@Value("${cors.allowed-origins}") List<String> allowedOrigins
	) {
		this.workspaceSocketHandler = workspaceSocketHandler;
		this.signalingSocketHandler = signalingSocketHandler;
		this.notificationSocketHandler = notificationSocketHandler;
		this.socketAuthInterceptor = socketAuthInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		String[] origins = allowedOrigins.toArray(String[]::new);
		registry.addHandler(workspaceSocketHandler, "/ws/workspaces")
			.addInterceptors(socketAuthInterceptor)
			.setAllowedOrigins(origins);
		registry.addHandler(signalingSocketHandler, "/ws/signaling")
			.addInterceptors(socketAuthInterceptor)
			.setAllowedOrigins(origins);
		registry.addHandler(notificationSocketHandler, "/ws/notifications")
			.addInterceptors(socketAuthInterceptor)
			.setAllowedOrigins(origins);
	}
}
