package com.mockdata.zipfit.workspace.entity;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.common.entity.BaseTimeEntity;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Workspace 멤버십. createdAt이 참여 시각이다. */
@Entity
@Table(
	name = "workspace_member",
	uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"}),
	indexes = @Index(columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private WorkspaceRole role;

	public WorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
		this.workspace = workspace;
		this.user = user;
		this.role = role;
	}

	public boolean isHost() {
		return role == WorkspaceRole.HOST;
	}

	public void changeRole(WorkspaceRole role) {
		this.role = role;
	}

	public Instant getJoinedAt() {
		return getCreatedAt();
	}
}
