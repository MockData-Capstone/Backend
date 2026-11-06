package com.mockdata.zipfit.vote.entity;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.candidate.entity.Candidate;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보지 투표 (VOTE-01). 멤버당 후보지 하나에 1표이며 철회는 없다.
 * 유일 제약으로 동시에 투표해도 한 표만 남는다.
 */
@Entity
@Table(
	name = "vote",
	uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "user_id"}),
	indexes = @Index(columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "candidate_id", nullable = false)
	private Candidate candidate;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private VoteChoice choice;

	public Vote(Candidate candidate, User user, VoteChoice choice) {
		this.candidate = candidate;
		this.user = user;
		this.choice = choice;
	}

	public void change(VoteChoice choice) {
		this.choice = choice;
	}
}
