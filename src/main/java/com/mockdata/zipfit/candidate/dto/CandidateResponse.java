package com.mockdata.zipfit.candidate.dto;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.workspace.dto.MemberProfile;
import java.time.Instant;

/**
 * 후보지 (CAND-02). 시설 정보는 저장하지 않으므로 kakaoPlaceId로 지도 API에서 다시 조회한다.
 *
 * @param addedBy 추가한 멤버. 탈퇴했으면 null
 */
public record CandidateResponse(
	Long candidateId,
	String name,
	String placeName,
	Double latitude,
	Double longitude,
	String kakaoPlaceId,
	Long recommendationId,
	MemberProfile addedBy,
	Instant createdAt,
	LocationResponse location,
	VoteSummary votes
) {

	public static CandidateResponse of(Candidate candidate, VoteSummary votes) {
		return new CandidateResponse(
			candidate.getId(),
			candidate.getName(),
			candidate.getPlaceName(),
			candidate.getLatitude(),
			candidate.getLongitude(),
			candidate.getKakaoPlaceId(),
			candidate.getRecommendationId(),
			candidate.getAddedBy() == null ? null : MemberProfile.of(candidate.getAddedBy()),
			candidate.getCreatedAt(),
			LocationResponse.of(candidate.getLocation()),
			votes
		);
	}
}
