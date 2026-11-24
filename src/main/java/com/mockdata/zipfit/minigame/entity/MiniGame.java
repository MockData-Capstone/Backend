package com.mockdata.zipfit.minigame.entity;

import com.mockdata.zipfit.minigame.entity.GameEnums.AbortReason;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameStatus;
import com.mockdata.zipfit.minigame.entity.GameEnums.GameType;
import com.mockdata.zipfit.minigame.entity.GameEnums.VisitorRule;
import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 방문자를 정하는 미니게임 (GAME-01). 확정된 결과는 수정·삭제할 수 없고 이력으로 남는다.
 * 회원이 탈퇴해도 이력이 남도록 회원은 ID와 닉네임으로 기록한다.
 */
@Entity
@Table(name = "mini_game", indexes = @Index(columnList = "workspace_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniGame {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private GameType type;

	/** 가위바위보만 */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private VisitorRule visitorRule;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GameStatus status;

	/** 진행 중일 때만 workspace ID. 유일 제약으로 진행 중인 게임을 Workspace당 1개로 막는다. */
	@Column(unique = true)
	private Long inProgressWorkspaceId;

	@Column(nullable = false)
	private Long startedByUserId;

	@Column(nullable = false, length = 20)
	private String startedByNickname;

	/** 결과가 확정되기 전에는 null */
	private Long visitorUserId;

	@Column(length = 20)
	private String visitorNickname;

	/** 가위바위보의 현재 라운드 (1부터). 룰렛은 0이다. */
	@Column(nullable = false)
	private int currentRound;

	/** 가위바위보 현재 라운드의 제출 마감. 지나면 내지 않은 참가자는 서버가 무작위로 고른다. */
	private Instant roundDeadline;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private AbortReason abortReason;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	private Instant finishedAt;

	public MiniGame(
		Workspace workspace, GameType type, VisitorRule visitorRule, Long startedByUserId, String startedByNickname
	) {
		this.workspace = workspace;
		this.type = type;
		this.visitorRule = type == GameType.ROCK_PAPER_SCISSORS ? visitorRule : null;
		this.status = GameStatus.IN_PROGRESS;
		this.inProgressWorkspaceId = workspace.getId();
		this.startedByUserId = startedByUserId;
		this.startedByNickname = startedByNickname;
	}

	public boolean isInProgress() {
		return status == GameStatus.IN_PROGRESS;
	}

	public void openRound(int roundNo, Instant deadline) {
		this.currentRound = roundNo;
		this.roundDeadline = deadline;
	}

	public void finish(MiniGameParticipant visitor, Instant now) {
		this.status = GameStatus.FINISHED;
		this.visitorUserId = visitor.getUserId();
		this.visitorNickname = visitor.getNickname();
		end(now);
	}

	public void abort(AbortReason reason, Instant now) {
		this.status = GameStatus.ABORTED;
		this.abortReason = reason;
		end(now);
	}

	private void end(Instant now) {
		this.inProgressWorkspaceId = null;
		this.roundDeadline = null;
		this.finishedAt = now;
	}
}
