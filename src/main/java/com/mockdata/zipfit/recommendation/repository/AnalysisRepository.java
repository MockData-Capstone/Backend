package com.mockdata.zipfit.recommendation.repository;

import com.mockdata.zipfit.recommendation.entity.Analysis;
import com.mockdata.zipfit.recommendation.entity.AnalysisStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	Optional<Analysis> findFirstByWorkspaceIdOrderByIdDesc(Long workspaceId);

	Optional<Analysis> findFirstByWorkspaceIdAndStatusOrderByIdDesc(Long workspaceId, AnalysisStatus status);

	boolean existsByWorkspaceIdAndStatus(Long workspaceId, AnalysisStatus status);

	List<Analysis> findAllByStatusAndRequestedAtBefore(AnalysisStatus status, Instant before);

	@Modifying(flushAutomatically = true)
	@Query("delete from Analysis a where a.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
