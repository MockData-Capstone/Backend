package com.mockdata.zipfit.tour.entity;

import com.mockdata.zipfit.tour.entity.TourEnums.ConnectionState;
import com.mockdata.zipfit.tour.entity.TourEnums.TourRole;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 투어 참여자 (TOUR-02). 나갔다가 다시 참여하면 같은 행을 다시 쓴다. */
@Entity
@Table(name = "tour_participant", uniqueConstraints = @UniqueConstraint(columnNames = {"tour_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tour_id", nullable = false)
	private TourSession tour;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TourRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ConnectionState connectionState;

	@Column(nullable = false)
	private Instant joinedAt;

	/** 나갔으면 퇴장 시각, 참여 중이면 null */
	private Instant leftAt;

	public TourParticipant(TourSession tour, Long userId, String nickname, TourRole role, Instant now) {
		this.tour = tour;
		this.userId = userId;
		this.nickname = nickname;
		this.role = role;
		this.connectionState = ConnectionState.CONNECTING;
		this.joinedAt = now;
	}

	public boolean isActive() {
		return leftAt == null;
	}

	public void rejoin(Instant now) {
		this.leftAt = null;
		this.joinedAt = now;
		this.connectionState = ConnectionState.CONNECTING;
	}

	public void leave(Instant now) {
		this.leftAt = now;
		this.connectionState = ConnectionState.DISCONNECTED;
	}

	public void changeState(ConnectionState state) {
		this.connectionState = state;
	}
}
