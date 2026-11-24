package com.mockdata.zipfit.minigame.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.minigame.dto.CurrentGameResponse;
import com.mockdata.zipfit.minigame.dto.GameResponse;
import com.mockdata.zipfit.minigame.dto.StartGameRequest;
import com.mockdata.zipfit.minigame.dto.SubmitChoiceRequest;
import com.mockdata.zipfit.minigame.service.MiniGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 게임 진행 상황은 Workspace 소켓의 GAME_STARTED, ROUND_OPENED, ROUND_RESULT, GAME_FINISHED 등으로 받는다. */
@RestController
@RequiredArgsConstructor
public class MiniGameController {

	private final MiniGameService gameService;

	/** GAME-01 미니게임 시작 */
	@PostMapping("/api/workspaces/{workspaceId}/minigames")
	@ResponseStatus(HttpStatus.CREATED)
	public GameResponse start(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody StartGameRequest request
	) {
		return gameService.start(workspaceId, userId, request);
	}

	/** GAME-05 진행 중인 미니게임 (없으면 game: null) */
	@GetMapping("/api/workspaces/{workspaceId}/minigames/current")
	public CurrentGameResponse getCurrent(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new CurrentGameResponse(gameService.getCurrent(workspaceId, userId).orElse(null));
	}

	/** GAME-06 미니게임 이력 (끝나거나 중단된 게임, 최신순) */
	@GetMapping("/api/workspaces/{workspaceId}/minigames")
	public ListResponse<GameResponse> getHistory(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(gameService.getHistory(workspaceId, userId));
	}

	/** GAME-03 미니게임 결과 조회 */
	@GetMapping("/api/minigames/{gameId}")
	public GameResponse get(@CurrentUser Long userId, @PathVariable Long gameId) {
		return gameService.get(gameId, userId);
	}

	/** GAME-02 룰렛 추첨 */
	@PostMapping("/api/minigames/{gameId}/draw")
	public GameResponse draw(@CurrentUser Long userId, @PathVariable Long gameId) {
		return gameService.draw(gameId, userId);
	}

	/** GAME-02 가위바위보 선택 제출 */
	@PostMapping("/api/minigames/{gameId}/rounds/{roundNo}/choices")
	public GameResponse submit(
		@CurrentUser Long userId,
		@PathVariable Long gameId,
		@PathVariable int roundNo,
		@Valid @RequestBody SubmitChoiceRequest request
	) {
		return gameService.submit(gameId, roundNo, userId, request.choice());
	}

	/** GAME-04 미니게임 중단 (시작한 멤버). 지우지 않고 ABORTED로 바꿔 이력에 남기므로 바뀐 게임을 돌려준다. */
	@DeleteMapping("/api/minigames/{gameId}")
	public GameResponse abort(@CurrentUser Long userId, @PathVariable Long gameId) {
		return gameService.abort(gameId, userId);
	}
}
