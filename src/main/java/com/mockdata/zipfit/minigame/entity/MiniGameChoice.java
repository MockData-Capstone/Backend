package com.mockdata.zipfit.minigame.entity;

import com.mockdata.zipfit.minigame.entity.GameEnums.Hand;
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

/** 가위바위보 라운드별 선택. 한 라운드에 한 번만 낼 수 있다. */
@Entity
@Table(
	name = "mini_game_choice",
	uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "round_no", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MiniGameChoice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false)
	private MiniGame game;

	@Column(name = "round_no", nullable = false)
	private int roundNo;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Hand hand;

	/** 제한 시간 안에 내지 않아 서버가 무작위로 골랐는지 */
	@Column(nullable = false)
	private boolean auto;

	public MiniGameChoice(MiniGame game, int roundNo, Long userId, Hand hand, boolean auto) {
		this.game = game;
		this.roundNo = roundNo;
		this.userId = userId;
		this.hand = hand;
		this.auto = auto;
	}
}
