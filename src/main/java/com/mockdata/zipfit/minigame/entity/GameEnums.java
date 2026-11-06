package com.mockdata.zipfit.minigame.entity;

/** 미니게임에서 쓰는 값들 */
public final class GameEnums {

	private GameEnums() {
	}

	/** 룰렛 / 가위바위보 */
	public enum GameType {
		ROULETTE, ROCK_PAPER_SCISSORS
	}

	/** 가위바위보에서 다음 라운드에 남는 쪽. 마지막 1명이 방문자다. */
	public enum VisitorRule {
		WINNER, LOSER
	}

	public enum GameStatus {
		IN_PROGRESS, FINISHED, ABORTED
	}

	/** 중단 사유: 시작한 멤버가 중단 / 참가자가 2명 미만이 됨 / Workspace 삭제 */
	public enum AbortReason {
		STOPPED_BY_STARTER, NOT_ENOUGH_PLAYERS, WORKSPACE_DELETED
	}

	/** 참가자 상태: 진행 중 / 라운드에서 탈락 / 나감·연결 끊김으로 제외 */
	public enum PlayerStatus {
		PLAYING, ELIMINATED, LEFT
	}

	public enum Hand {
		ROCK, PAPER, SCISSORS;

		public boolean beats(Hand other) {
			return switch (this) {
				case ROCK -> other == SCISSORS;
				case PAPER -> other == ROCK;
				case SCISSORS -> other == PAPER;
			};
		}
	}
}
