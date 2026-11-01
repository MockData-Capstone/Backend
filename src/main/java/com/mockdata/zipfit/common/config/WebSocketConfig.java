package com.mockdata.zipfit.common.config;

import com.mockdata.zipfit.chat.socket.WorkspaceSocketHandler;
import com.mockdata.zipfit.common.websocket.SocketAuthInterceptor;
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
	private final SocketAuthInterceptor socketAuthInterceptor;
	private final List<String> allowedOrigins;

	public WebSocketConfig(
		WorkspaceSocketHandler workspaceSocketHandler,
		SocketAuthInterceptor socketAuthInterceptor,
		@Value("${cors.allowed-origins}") List<String> allowedOrigins
	) {
		this.workspaceSocketHandler = workspaceSocketHandler;
		this.socketAuthInterceptor = socketAuthInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(workspaceSocketHandler, "/ws/workspaces")
			.addInterceptors(socketAuthInterceptor)
			.setAllowedOrigins(allowedOrigins.toArray(String[]::new));
	}
}
