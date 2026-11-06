package com.mockdata.zipfit.recommendation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추천 위치. 좌표·장소명·추천 이유만 저장하고 시설 정보는 저장하지 않는다 (NFR-013).
 * 사용자가 수정·삭제할 수 없다.
 */
@Entity
@Table(name = "recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation {

	public static final int REASON_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "analysis_id", nullable = false)
	private Analysis analysis;

	@Column(nullable = false, length = 100)
	private String placeName;

	/** 좌표를 얻지 못했으면 null (지도 핀 없이 목록에만 표시) */
	private Double latitude;

	private Double longitude;

	@Column(length = REASON_MAX_LENGTH)
	private String reason;

	@Column(length = 30)
	private String kakaoPlaceId;

	public Recommendation(
		Analysis analysis, String placeName, Double latitude, Double longitude, String reason, String kakaoPlaceId
	) {
		this.analysis = analysis;
		this.placeName = placeName;
		this.latitude = latitude;
		this.longitude = longitude;
		this.reason = reason;
		this.kakaoPlaceId = kakaoPlaceId;
	}

	public boolean hasCoordinates() {
		return latitude != null && longitude != null;
	}
}
