package com.mockdata.zipfit.minigame.repository;

import com.mockdata.zipfit.minigame.entity.MiniGameParticipant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MiniGameParticipantRepository extends JpaRepository<MiniGameParticipant, Long> {

	List<MiniGameParticipant> findAllByGameIdOrderByIdAsc(Long gameId);

	List<MiniGameParticipant> findAllByGameIdInOrderByIdAsc(Collection<Long> gameIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from MiniGameParticipant p where p.game.id in (select g.id from MiniGame g where g.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceId(Long workspaceId);
}
