package com.mockdata.zipfit.candidate.entity;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * 후보지 (CAND-01). 좌표와 이름만 저장하고, 시설 정보는 볼 때마다 지도 API로 다시 조회한다 (FR-030, NFR-013).
 * 재분석으로 추천이 바뀌어도 후보지는 유지된다 (FR-015).
 */
@Entity
@Table(name = "candidate", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "latitude", "longitude"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate {

	public static final int NAME_MAX_LENGTH = 50;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	/** 추가할 때 쓴 추천 위치. 재분석 뒤에는 남아 있지 않을 수 있어 외래 키로 묶지 않는다. */
	private Long recommendationId;

	/** 멤버가 바꿀 수 있는 이름. 기본값은 장소명이다. */
	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(nullable = false, length = 100)
	private String placeName;

	@Column(nullable = false)
	private Double latitude;

	@Column(nullable = false)
	private Double longitude;

	@Column(length = 30)
	private String kakaoPlaceId;

	/** 추가한 멤버. 탈퇴하면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "added_by")
	private User addedBy;

	@Embedded
	private LocationInfo location;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public Candidate(
		Workspace workspace,
		Long recommendationId,
		String placeName,
		Double latitude,
		Double longitude,
		String kakaoPlaceId,
		User addedBy,
		LocationInfo location
	) {
		this.workspace = workspace;
		this.recommendationId = recommendationId;
		this.name = placeName.length() > NAME_MAX_LENGTH ? placeName.substring(0, NAME_MAX_LENGTH) : placeName;
		this.placeName = placeName;
		this.latitude = latitude;
		this.longitude = longitude;
		this.kakaoPlaceId = kakaoPlaceId;
		this.addedBy = addedBy;
		this.location = location;
	}

	public void rename(String name) {
		this.name = name;
	}

	public boolean isAddedBy(Long userId) {
		return addedBy != null && addedBy.getId().equals(userId);
	}
}
