package com.mockdata.zipfit.common.event;

/** 후보지를 지우기 직전에 같은 트랜잭션 안에서 동기로 발행한다. 투표 도메인이 연관 투표를 지운다 (CAND-03). */
public record CandidateRemovingEvent(Long candidateId) {
}
