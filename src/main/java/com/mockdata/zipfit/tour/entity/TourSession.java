package com.mockdata.zipfit.tour.entity;

import com.mockdata.zipfit.tour.entity.TourEnums.EndReason;
import com.mockdata.zipfit.tour.entity.TourEnums.TourStatus;
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
 * 실시간 투어 세션 (TOUR-01). 시작·종료 시각만 남기고 영상은 저장하지 않는다 (NFR-008).
 * 회원이 탈퇴해도 이력이 남도록 방문자는 ID와 닉네임으로 기록한다.
 */
@Entity
@Table(name = "tour_session", indexes = @Index(columnList = "workspace_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	/** 방문자를 정한 미니게임. 한 게임 결과로는 투어를 한 번만 한다. */
	@Column(nullable = false, unique = true)
	private Long gameId;

	@Column(nullable = false)
	private Long visitorUserId;

	@Column(nullable = false, length = 20)
	private String visitorNickname;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TourStatus status;

	/** 진행 중일 때만 workspace ID. 유일 제약으로 진행 중인 투어를 Workspace당 1개로 막는다 (제안). */
	@Column(unique = true)
	private Long inProgressWorkspaceId;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private EndReason endReason;

	/** 방문자 연결이 끊긴 시각. 제한 시간 안에 돌아오지 않으면 투어를 끝낸다 (TOUR-05). */
	private Instant visitorDisconnectedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	private Instant endedAt;

	public TourSession(Workspace workspace, Long gameId, Long visitorUserId, String visitorNickname) {
		this.workspace = workspace;
		this.gameId = gameId;
		this.visitorUserId = visitorUserId;
		this.visitorNickname = visitorNickname;
		this.status = TourStatus.IN_PROGRESS;
		this.inProgressWorkspaceId = workspace.getId();
	}

	public boolean isInProgress() {
		return status == TourStatus.IN_PROGRESS;
	}

	public boolean isVisitor(Long userId) {
		return visitorUserId.equals(userId);
	}

	public void visitorDisconnected(Instant now) {
		if (visitorDisconnectedAt == null) {
			this.visitorDisconnectedAt = now;
		}
	}

	public void visitorReturned() {
		this.visitorDisconnectedAt = null;
	}

	public void end(EndReason reason, Instant now) {
		this.status = TourStatus.ENDED;
		this.endReason = reason;
		this.inProgressWorkspaceId = null;
		this.endedAt = now;
	}
}
