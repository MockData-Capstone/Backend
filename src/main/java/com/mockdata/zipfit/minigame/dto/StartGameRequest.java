package com.mockdata.zipfit.minigame.dto;

import com.mockdata.zipfit.minigame.entity.GameEnums.GameType;
import com.mockdata.zipfit.minigame.entity.GameEnums.VisitorRule;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * GAME-01
 *
 * @param participantUserIds 참여 멤버. 지금 접속 중인 멤버 중에서 2명 이상
 * @param visitorRule        가위바위보만: 승자/패자 중 방문자가 될 쪽
 */
public record StartGameRequest(@NotNull GameType type, @NotNull List<Long> participantUserIds, VisitorRule visitorRule) {
}
