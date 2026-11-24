package com.mockdata.zipfit.candidate.dto;

/**
 * 후보지 투표 집계 요약 (CAND-02, VOTE-02)
 *
 * @param split    찬성·반대가 각각 1표 이상이면 true ("의견이 갈림", 제안)
 * @param myChoice 내 표: AGREE / DISAGREE, 투표하지 않았으면 null
 */
public record VoteSummary(long agreeCount, long disagreeCount, boolean split, String myChoice) {

	public static final VoteSummary EMPTY = new VoteSummary(0, 0, false, null);
}
