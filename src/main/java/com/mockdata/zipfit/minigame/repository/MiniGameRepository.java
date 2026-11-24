package com.mockdata.zipfit.minigame.repository;

import com.mockdata.zipfit.minigame.entity.GameEnums.GameStatus;
import com.mockdata.zipfit.minigame.entity.MiniGame;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MiniGameRepository extends JpaRepository<MiniGame, Long> {

	/** 제출·판정·중단이 동시에 일어나도 한 번만 처리되도록 게임 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from MiniGame g where g.id = :id")
	Optional<MiniGame> findForUpdate(Long id);

	Optional<MiniGame> findFirstByWorkspaceIdAndStatus(Long workspaceId, GameStatus status);

	List<MiniGame> findAllByWorkspaceIdAndStatusInOrderByIdDesc(Long workspaceId, Collection<GameStatus> statuses);

	/** 투어 시작(TOUR-01): 가장 최근에 확정된 게임 */
	Optional<MiniGame> findFirstByWorkspaceIdAndStatusOrderByIdDesc(Long workspaceId, GameStatus status);

	List<MiniGame> findAllByStatus(GameStatus status);

	@Modifying(flushAutomatically = true)
	@Query("delete from MiniGame g where g.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
