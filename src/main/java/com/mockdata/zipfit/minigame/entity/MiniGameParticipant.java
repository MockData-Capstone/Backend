package com.mockdata.zipfit.minigame.entity;

import com.mockdata.zipfit.minigame.entity.GameEnums.PlayerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 미니게임 참가자 */
@Entity
@Table(name = "mini_game_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniGameParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false)
	private MiniGame game;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PlayerStatus status;

	/** 탈락하거나 빠진 라운드 */
	private Integer outRound;

	public MiniGameParticipant(MiniGame game, Long userId, String nickname) {
		this.game = game;
		this.userId = userId;
		this.nickname = nickname;
		this.status = PlayerStatus.PLAYING;
	}

	public boolean isPlaying() {
		return status == PlayerStatus.PLAYING;
	}

	public void eliminate(int roundNo) {
		this.status = PlayerStatus.ELIMINATED;
		this.outRound = roundNo;
	}

	public void leave(int roundNo) {
		this.status = PlayerStatus.LEFT;
		this.outRound = roundNo;
	}
}
