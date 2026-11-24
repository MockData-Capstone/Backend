package com.mockdata.zipfit.candidate.dto;

import jakarta.validation.constraints.NotNull;

/** CAND-01 추천 위치를 후보지로 추가한다. */
public record AddCandidateRequest(@NotNull Long recommendationId) {
}
