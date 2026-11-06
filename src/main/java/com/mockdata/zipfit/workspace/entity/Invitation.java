package com.mockdata.zipfit.workspace.entity;

import com.mockdata.zipfit.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 초대 링크 (MEM-01). Workspace당 유효한 링크는 1개다.
 * 호스트가 창을 다시 열면 같은 링크를 보여줘야 해서 토큰 원문을 저장한다.
 */
@Entity
@Table(name = "invitation", indexes = @Index(columnList = "workspace_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Column(nullable = false, unique = true, length = 64)
	private String token;

	@Column(nullable = false)
	private Instant expiresAt;

	public Invitation(Workspace workspace, String token, Instant expiresAt) {
		this.workspace = workspace;
		this.token = token;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}
}
