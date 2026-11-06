package com.mockdata.zipfit.minigame.dto;

/** GAME-05 진행 중인 게임이 없으면 game이 null이다. */
public record CurrentGameResponse(GameResponse game) {
}
