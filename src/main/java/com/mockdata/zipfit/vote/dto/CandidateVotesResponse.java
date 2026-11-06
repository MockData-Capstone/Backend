package com.mockdata.zipfit.vote.dto;

import com.mockdata.zipfit.vote.entity.VoteChoice;
import java.util.List;

/**
 * 후보지 하나의 투표 결과 (VOTE-02). VOTE_UPDATED의 data로도 쓴다.
 *
 * @param split    찬성·반대가 각각 1표 이상이면 true ("의견이 갈림", 제안)
 * @param myChoice 내 표. 투표하지 않았거나 실시간 통지(VOTE_UPDATED)면 null이다. 통지에서는 votes에서 내 표를 찾는다.
 * @param votes    멤버별 선택
 */
public record CandidateVotesResponse(
	Long candidateId,
	String candidateName,
	long agreeCount,
	long disagreeCount,
	boolean split,
	VoteChoice myChoice,
	List<Voter> votes
) {

	public record Voter(Long userId, String nickname, String profileImageUrl, VoteChoice choice) {
	}
}
