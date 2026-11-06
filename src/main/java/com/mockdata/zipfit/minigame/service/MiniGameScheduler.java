package com.mockdata.zipfit.minigame.service;

import com.mockdata.zipfit.chat.socket.WorkspaceSessionRegistry;
import com.mockdata.zipfit.minigame.entity.MiniGame;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 진행 중인 게임을 주기적으로 확인한다 (GAME-02).
 * <ul>
 *   <li>가위바위보 라운드 마감이 지나면 내지 않은 참가자 대신 무작위로 고른다.</li>
 *   <li>Workspace 소켓 연결이 끊긴 채 유예 시간이 지난 참가자는 결과에서 뺀다. 새로고침 같은 짧은 끊김은 봐준다.</li>
 * </ul>
 */
@Slf4j
@Component
public class MiniGameScheduler {

	private final MiniGameService gameService;
	private final WorkspaceSessionRegistry sessionRegistry;
	private final Duration disconnectGrace;
	/** "게임ID:회원ID" → 연결 끊김을 처음 본 시각 */
	private final Map<String, Instant> disconnectedSince = new ConcurrentHashMap<>();

	public MiniGameScheduler(
		MiniGameService gameService,
		WorkspaceSessionRegistry sessionRegistry,
		@Value("${game.disconnect-grace:10s}") Duration disconnectGrace
	) {
		this.gameService = gameService;
		this.sessionRegistry = sessionRegistry;
		this.disconnectGrace = disconnectGrace;
	}

	@Scheduled(fixedDelayString = "${game.check-interval:1s}")
	public void tick() {
		check(Instant.now());
	}

	public void check(Instant now) {
		for (MiniGame game : gameService.findInProgress()) {
			try {
				removeDisconnected(game, now);
				gameService.closeRoundIfExpired(game.getId(), now);
			} catch (RuntimeException e) {
				log.warn("미니게임 확인 실패: gameId={}", game.getId(), e);
			}
		}
		disconnectedSince.keySet().removeIf(key -> gameService.findInProgress().stream()
			.noneMatch(game -> key.startsWith(game.getId() + ":")));
	}

	private void removeDisconnected(MiniGame game, Instant now) {
		Long workspaceId = game.getWorkspace().getId();
		for (Long userId : gameService.findPlayingUserIds(game.getId())) {
			String key = game.getId() + ":" + userId;
			if (sessionRegistry.isConnected(workspaceId, userId)) {
				disconnectedSince.remove(key);
				continue;
			}
			Instant since = disconnectedSince.computeIfAbsent(key, k -> now);
			if (!since.plus(disconnectGrace).isAfter(now)) {
				disconnectedSince.remove(key);
				gameService.removePlayer(game.getId(), userId);
			}
		}
	}
}
