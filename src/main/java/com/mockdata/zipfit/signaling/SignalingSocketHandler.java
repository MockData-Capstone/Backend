package com.mockdata.zipfit.signaling;

import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.websocket.SocketSessions;
import com.mockdata.zipfit.common.websocket.WsCloseCodes;
import com.mockdata.zipfit.common.websocket.WsMessage;
import com.mockdata.zipfit.tour.dto.TourResponse;
import com.mockdata.zipfit.tour.entity.TourEnums.ConnectionState;
import com.mockdata.zipfit.tour.entity.TourParticipant;
import com.mockdata.zipfit.tour.service.TourService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 시그널링 소켓 /ws/signaling?tourId={id}&token={accessToken} (SIG-01~05).
 * WebRTC 연결 정보(SDP·ICE)를 지정한 상대에게만 중계하고 저장하지 않는다. 영상은 이 경로로 지나가지 않는다 (NFR-008).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingSocketHandler extends TextWebSocketHandler {

	// 클라이언트 → 서버
	public static final String JOIN = "JOIN";
	public static final String CONNECTION_STATE = "CONNECTION_STATE";
	/** 지정한 상대에게만 중계하는 연결 협상 메시지 */
	public static final List<String> RELAYED = List.of("OFFER", "ANSWER", "ICE_CANDIDATE");
	// 서버 → 클라이언트
	public static final String JOINED = "JOINED";
	public static final String PEER_JOINED = "PEER_JOINED";
	public static final String PEER_LEFT = "PEER_LEFT";
	public static final String PEER_STATE = "PEER_STATE";

	private static final String TOUR_ID = "tourId";
	/** SDP는 기본 버퍼(8KB)를 넘을 수 있다. */
	private static final int MAX_MESSAGE_BYTES = 64 * 1024;

	private final SocketSessions socketSessions;
	private final SignalingRoomRegistry rooms;
	private final TourService tourService;
	private final JsonMapper jsonMapper;

	@Override
	public void afterConnectionEstablished(WebSocketSession raw) {
		Optional<CloseStatus> rejection = SocketSessions.rejection(raw);
		if (rejection.isPresent()) {
			socketSessions.close(raw, rejection.get());
			return;
		}
		Optional<Long> tourId = SocketSessions.longQueryParam(raw, TOUR_ID);
		if (tourId.isEmpty()) {
			socketSessions.close(raw, WsCloseCodes.BAD_REQUEST);
			return;
		}
		try {
			tourService.requireActiveParticipant(tourId.get(), SocketSessions.userId(raw));
		} catch (BusinessException e) {
			socketSessions.close(raw, e.getErrorCode() == ErrorCode.NOT_TOUR_PARTICIPANT
				? WsCloseCodes.FORBIDDEN : WsCloseCodes.NOT_FOUND);
			return;
		}
		raw.getAttributes().put(TOUR_ID, tourId.get());
		raw.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
		rooms.register(tourId.get(), SocketSessions.userId(raw), socketSessions.track(raw))
			.ifPresent(previous -> socketSessions.close(previous, WsCloseCodes.REPLACED));
	}

	@Override
	protected void handleTextMessage(WebSocketSession raw, TextMessage message) {
		WebSocketSession session = socketSessions.find(raw).orElse(null);
		if (session == null) {
			return;
		}
		Long tourId = (Long) session.getAttributes().get(TOUR_ID);
		Long userId = SocketSessions.userId(session);
		Incoming incoming;
		try {
			incoming = jsonMapper.readValue(message.getPayload(), Incoming.class);
		} catch (JacksonException e) {
			sendError(session, ErrorCode.INVALID_SOCKET_MESSAGE);
			return;
		}
		if (JOIN.equals(incoming.type())) {
			join(session, tourId, userId);
		} else if (!rooms.isJoined(session)) {
			sendError(session, ErrorCode.NOT_JOINED);
		} else if (RELAYED.contains(incoming.type())) {
			relay(session, tourId, userId, incoming);
		} else if (CONNECTION_STATE.equals(incoming.type())) {
			reportState(session, tourId, userId, incoming.data());
		} else {
			sendError(session, ErrorCode.UNSUPPORTED_SOCKET_MESSAGE);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
		socketSessions.untrack(raw);
		Long tourId = (Long) raw.getAttributes().get(TOUR_ID);
		if (tourId == null) {
			return;
		}
		Long userId = SocketSessions.userId(raw);
		// 같은 회원이 새로 접속해 바뀐 연결이면 퇴장으로 보지 않는다.
		if (rooms.unregister(tourId, userId, raw) && rooms.isJoined(raw)) {
			rooms.broadcastExcept(tourId, userId, WsMessage.of(PEER_LEFT, new Peer(userId, null, null)));
			tourService.reportConnectionState(tourId, userId, ConnectionState.DISCONNECTED);
		}
	}

	/** SIG-01·03 입장: 기존 참여자 목록과 방문자를 돌려주고 다른 참여자에게 알린다. */
	private void join(WebSocketSession session, Long tourId, Long userId) {
		TourParticipant me;
		try {
			me = tourService.requireActiveParticipant(tourId, userId);
		} catch (BusinessException e) {
			socketSessions.close(session, WsCloseCodes.FORBIDDEN);
			return;
		}
		boolean firstJoin = !rooms.isJoined(session);
		rooms.markJoined(session);
		tourService.reportConnectionState(tourId, userId, ConnectionState.CONNECTING);
		TourResponse tour = tourService.getTour(tourId);
		List<TourResponse.Participant> peers = tour.participants().stream()
			.filter(p -> !p.userId().equals(userId))
			.filter(p -> rooms.find(tourId, p.userId()).map(rooms::isJoined).orElse(false))
			.toList();
		socketSessions.send(session, WsMessage.of(JOINED, new Joined(tourId, userId, tour.visitor().userId(), peers)));
		if (firstJoin) {
			rooms.broadcastExcept(tourId, userId,
				WsMessage.of(PEER_JOINED, new Peer(userId, me.getNickname(), me.getRole().name())));
		}
	}

	/** SIG-02 보낸 쪽이 지정한 상대에게만 전달한다. 받는 쪽은 fromUserId로 보낸 사람을 안다. */
	private void relay(WebSocketSession session, Long tourId, Long userId, Incoming incoming) {
		JsonNode target = incoming.data() == null ? null : incoming.data().get("targetUserId");
		if (!(incoming.data() instanceof ObjectNode data) || target == null || !target.canConvertToLong()) {
			sendError(session, ErrorCode.INVALID_SOCKET_MESSAGE);
			return;
		}
		Optional<WebSocketSession> peer = rooms.find(tourId, target.longValue()).filter(rooms::isJoined);
		if (peer.isEmpty()) {
			sendError(session, ErrorCode.PEER_NOT_FOUND);
			return;
		}
		data.put("fromUserId", userId);
		socketSessions.send(peer.get(), WsMessage.of(incoming.type(), data));
	}

	/** SIG-04 연결 상태를 반영하고 다른 참여자에게 알린다(방문자 연결 끊김 표시). */
	private void reportState(WebSocketSession session, Long tourId, Long userId, JsonNode data) {
		ConnectionState state;
		try {
			state = ConnectionState.valueOf(data.get("state").asString());
		} catch (RuntimeException e) {
			sendError(session, ErrorCode.INVALID_SOCKET_MESSAGE);
			return;
		}
		tourService.reportConnectionState(tourId, userId, state);
		rooms.broadcastExcept(tourId, userId, WsMessage.of(PEER_STATE, new PeerState(userId, state)));
	}

	private void sendError(WebSocketSession session, ErrorCode errorCode) {
		socketSessions.send(session, WsMessage.error(errorCode.name(), errorCode.getMessage(), null));
	}

	record Incoming(String type, JsonNode data) {
	}

	/** @param peers JOIN한 다른 참여자 */
	public record Joined(Long tourId, Long selfUserId, Long visitorUserId, List<TourResponse.Participant> peers) {
	}

	public record Peer(Long userId, String nickname, String role) {
	}

	public record PeerState(Long userId, ConnectionState state) {
	}
}
