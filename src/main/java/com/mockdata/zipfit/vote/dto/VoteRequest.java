package com.mockdata.zipfit.vote.dto;

import com.mockdata.zipfit.vote.entity.VoteChoice;
import jakarta.validation.constraints.NotNull;

/** VOTE-01·03 */
public record VoteRequest(@NotNull VoteChoice choice) {
}
