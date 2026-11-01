package com.mockdata.zipfit.minigame.dto;

import com.mockdata.zipfit.minigame.entity.GameEnums.Hand;
import jakarta.validation.constraints.NotNull;

/** GAME-02 가위바위보 선택: ROCK / PAPER / SCISSORS */
public record SubmitChoiceRequest(@NotNull Hand choice) {
}
