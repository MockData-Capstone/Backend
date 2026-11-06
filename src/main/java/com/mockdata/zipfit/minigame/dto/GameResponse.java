package com.mockdata.zipfit.minigame.dto;

import com.mockdata.zipfit.minigame.entity.GameEnums.AbortReason;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameStatus;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameType;
import com.mockdata.zipfit.minigame.entity.GameEnums.Hand;
import com.mockdata.zipfit.minigame.entity.GameEnums.PlayerStatus;
import com.mockdata.zipfit.minigame.entity.GameEnums.VisitorRule;
import java.time.Instant;
import java.util.List;

/**
 * 미니게임 (GAME-03·05·06). GAME_STARTED의 data로도 쓴다.
 *
 * @param currentRound     가위바위보의 현재 라운드. 룰렛은 0
 * @param roundDeadline    현재 라운드 제출 마감. 지나면 서버가 대신 고른다
 * @param submittedUserIds 현재 라운드에 낸 참가자. 무엇을 냈는지는 라운드가 끝나야 공개한다
 * @param rounds           끝난 라운드 결과
 * @param visitor          결과가 확정되기 전에는 null
 */
public record GameResponse(
	Long gameId,
	Long workspaceId,
	GameType type,
	VisitorRule visitorRule,
	GameStatus status,
	Player startedBy,
	List<Participant> participants,
	int currentRound,
	Instant roundDeadline,
	List<Long> submittedUserIds,
	List<Round> rounds,
	Player visitor,
	AbortReason abortReason,
	Instant startedAt,
	Instant finishedAt
) {

	public record Player(Long userId, String nickname) {
	}

	/** @param outRound 탈락하거나 빠진 라운드 */
	public record Participant(Long userId, String nickname, PlayerStatus status, Integer outRound) {
	}

	/** @param draw 세 가지가 모두 나오거나 전원이 같으면 무승부로 같은 멤버가 다시 한다 */
	public record Round(int roundNo, boolean draw, List<Choice> choices, List<Long> eliminatedUserIds) {
	}

	/** @param auto 제한 시간 안에 내지 않아 서버가 무작위로 골랐다 */
	public record Choice(Long userId, String nickname, Hand choice, boolean auto) {
	}
}
