package com.mockdata.zipfit.minigame.repository;

import com.mockdata.zipfit.minigame.entity.MiniGameChoice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MiniGameChoiceRepository extends JpaRepository<MiniGameChoice, Long> {

	List<MiniGameChoice> findAllByGameIdOrderByRoundNoAscIdAsc(Long gameId);

	List<MiniGameChoice> findAllByGameIdAndRoundNo(Long gameId, int roundNo);

	boolean existsByGameIdAndRoundNoAndUserId(Long gameId, int roundNo, Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from MiniGameChoice c where c.game.id in (select g.id from MiniGame g where g.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceId(Long workspaceId);
}
