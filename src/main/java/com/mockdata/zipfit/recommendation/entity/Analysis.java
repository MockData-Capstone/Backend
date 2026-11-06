package com.mockdata.zipfit.recommendation.entity;

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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** AI 분석 요청 (REC-01). 추출 조건을 함께 저장해 조건 확정(COND-01)의 초기값으로 쓴다. */
@Entity
@Table(name = "analysis", indexes = @Index(columnList = "workspace_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnalysisStatus status;

	/**
	 * 분석 중일 때만 workspace ID를 넣고 끝나면 비운다.
	 * 유일 제약으로 같은 Workspace에서 분석이 동시에 두 개 시작되지 않게 한다 (FR-010).
	 */
	@Column(unique = true)
	private Long inProgressWorkspaceId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private AnalysisFailure failure;

	/** 추출 조건(ExtractedConditions) JSON */
	@Lob
	private String conditionsJson;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant requestedAt;

	private Instant completedAt;

	public Analysis(Workspace workspace) {
		this.workspace = workspace;
		this.status = AnalysisStatus.IN_PROGRESS;
		this.inProgressWorkspaceId = workspace.getId();
	}

	public void complete(String conditionsJson, Instant now) {
		this.status = AnalysisStatus.COMPLETED;
		this.conditionsJson = conditionsJson;
		finish(now);
	}

	public void fail(AnalysisFailure failure, String conditionsJson, Instant now) {
		this.status = AnalysisStatus.FAILED;
		this.failure = failure;
		this.conditionsJson = conditionsJson;
		finish(now);
	}

	public boolean isInProgress() {
		return status == AnalysisStatus.IN_PROGRESS;
	}

	private void finish(Instant now) {
		this.inProgressWorkspaceId = null;
		this.completedAt = now;
	}
}
