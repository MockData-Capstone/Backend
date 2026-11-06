package com.mockdata.zipfit.candidate.repository;

import com.mockdata.zipfit.candidate.entity.Candidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

	@EntityGraph(attributePaths = "addedBy")
	List<Candidate> findAllByWorkspaceIdOrderByIdAsc(Long workspaceId);

	@EntityGraph(attributePaths = {"addedBy", "workspace"})
	Optional<Candidate> findWithAddedById(Long id);

	boolean existsByWorkspaceIdAndLatitudeAndLongitude(Long workspaceId, Double latitude, Double longitude);

	List<Candidate> findAllByWorkspaceIdAndRecommendationIdIn(Long workspaceId, Collection<Long> recommendationIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from Candidate c where c.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);

	@Modifying(flushAutomatically = true)
	@Query("update Candidate c set c.addedBy = null where c.addedBy.id = :userId")
	int clearAddedBy(Long userId);
}
