package com.mockdata.zipfit.minigame.dto;

import com.mockdata.zipfit.minigame.entity.GameEnums.AbortReason;
import java.time.Instant;
import java.util.List;

/**
 * 미니게임 진행 통지(SIG-06)의 type과 data. Workspace 소켓으로 모든 멤버에게 동시에 보낸다.
 * 방문자는 서버가 정한 뒤에야 GAME_FINISHED로 공개된다.
 */
public final class GameEvents {

	public static final String GAME_STARTED = "GAME_STARTED";
	public static final String ROUND_OPENED = "ROUND_OPENED";
	public static final String ROUND_RESULT = "ROUND_RESULT";
	public static final String GAME_PLAYER_LEFT = "GAME_PLAYER_LEFT";
	public static final String GAME_FINISHED = "GAME_FINISHED";
	public static final String GAME_ABORTED = "GAME_ABORTED";

	private GameEvents() {
	}

	public record RoundOpened(Long gameId, int roundNo, List<Long> playerUserIds, Instant deadline) {
	}

	public record RoundResult(Long gameId, GameResponse.Round round, List<Long> remainingUserIds) {
	}

	/** 나가거나 연결이 끊겨 결과에서 빠진 참가자 */
	public record PlayerLeft(Long gameId, Long userId, String nickname) {
	}

	public record GameFinished(Long gameId, GameResponse.Player visitor) {
	}

	public record GameAborted(Long gameId, AbortReason reason) {
	}
}
