package com.mockdata.zipfit.common.websocket;

import com.mockdata.zipfit.common.security.AccessTokenResult;
import com.mockdata.zipfit.common.security.JwtProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket 접속 인증. 브라우저 WebSocket은 헤더를 붙일 수 없어 접속 주소의 token 쿼리 파라미터로 접근 토큰을 받는다.
 * 핸드셰이크는 항상 받아들이고, 실패하면 사유를 {@link SocketSessions#REJECT}에 남겨 핸들러가 close code로 닫게 한다.
 */
@Component
@RequiredArgsConstructor
public class SocketAuthInterceptor implements HandshakeInterceptor {

	private final JwtProvider jwtProvider;

	@Override
	public boolean beforeHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Map<String, Object> attributes
	) {
		Map<String, String> params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams()
			.toSingleValueMap();
		attributes.put(SocketSessions.QUERY, params);
		String token = params.get("token");
		if (token == null || token.isBlank()) {
			attributes.put(SocketSessions.REJECT, WsCloseCodes.UNAUTHORIZED);
			return true;
		}
		CloseStatus reject = switch (jwtProvider.parseAccessToken(token)) {
			case AccessTokenResult.Valid valid -> {
				attributes.put(SocketSessions.USER_ID, valid.userId());
				attributes.put(SocketSessions.TOKEN_EXPIRES_AT, valid.expiresAt());
				yield null;
			}
			case AccessTokenResult.Expired expired -> WsCloseCodes.TOKEN_EXPIRED;
			case AccessTokenResult.Invalid invalid -> WsCloseCodes.UNAUTHORIZED;
		};
		if (reject != null) {
			attributes.put(SocketSessions.REJECT, reject);
		}
		return true;
	}

	@Override
	public void afterHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Exception exception
	) {
	}
}
