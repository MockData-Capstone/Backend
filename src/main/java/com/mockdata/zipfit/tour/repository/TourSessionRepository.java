package com.mockdata.zipfit.tour.repository;

import com.mockdata.zipfit.tour.entity.TourEnums.TourStatus;
import com.mockdata.zipfit.tour.entity.TourSession;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TourSessionRepository extends JpaRepository<TourSession, Long> {

	/** 참여·나가기·종료가 동시에 일어나도 인원과 상태가 어긋나지 않도록 세션을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from TourSession t where t.id = :id")
	Optional<TourSession> findForUpdate(Long id);

	Optional<TourSession> findFirstByWorkspaceIdAndStatus(Long workspaceId, TourStatus status);

	List<TourSession> findAllByWorkspaceIdAndStatusOrderByIdDesc(Long workspaceId, TourStatus status);

	List<TourSession> findAllByStatusAndVisitorDisconnectedAtIsNotNull(TourStatus status);

	boolean existsByGameId(Long gameId);

	@Modifying(flushAutomatically = true)
	@Query("delete from TourSession t where t.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
