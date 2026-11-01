package com.mockdata.zipfit.condition.entity;

import com.mockdata.zipfit.workspace.entity.Workspace;
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

/** 멤버가 받아들인 조건 제안 (REC-03). 재분석하면 새 분석 기준으로 다시 제안한다. */
@Entity
@Table(
	name = "accepted_suggestion",
	uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "analysis_id", "type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcceptedSuggestion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	@Column(name = "analysis_id", nullable = false)
	private Long analysisId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SuggestionType type;

	public AcceptedSuggestion(Workspace workspace, Long analysisId, SuggestionType type) {
		this.workspace = workspace;
		this.analysisId = analysisId;
		this.type = type;
	}
}
