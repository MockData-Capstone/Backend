package com.mockdata.zipfit.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 (NOTI-01). 같은 회원이 여러 기기로 접속해도 한 번만 기록한다.
 * 사용자는 내용을 수정·삭제할 수 없고 읽음 처리만 한다.
 */
@Entity
@Table(name = "notification", indexes = {
	@Index(columnList = "user_id, updated_at"),
	@Index(columnList = "workspace_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	public static final int MESSAGE_MAX_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 받는 회원 */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "workspace_id", nullable = false)
	private Long workspaceId;

	/** 알림을 만들 때의 Workspace 이름 */
	@Column(nullable = false, length = 30)
	private String workspaceName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false, length = MESSAGE_MAX_LENGTH)
	private String message;

	/** 이동할 대상 (후보지 ID, 투어 ID 등). 유형에 따라 없다. */
	private Long targetId;

	/** 묶인 건수. 새 메시지 알림만 1보다 커진다. */
	@Column(nullable = false)
	private int count;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/** 마지막으로 갱신된 시각. 최신순 정렬과 보관 기간(2주)의 기준이다. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Notification(
		Long userId, Long workspaceId, String workspaceName, NotificationType type, String message, Long targetId, Instant now
	) {
		this.userId = userId;
		this.workspaceId = workspaceId;
		this.workspaceName = workspaceName;
		this.type = type;
		this.message = message;
		this.targetId = targetId;
		this.count = 1;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 읽지 않은 새 메시지 알림에 한 건을 더한다. */
	public void addMessage(String message, Instant now) {
		this.count++;
		this.message = message;
		this.updatedAt = now;
	}

	/** @return 읽지 않았다가 읽음으로 바뀌었으면 true */
	public boolean markRead() {
		if (read) {
			return false;
		}
		this.read = true;
		return true;
	}
}
