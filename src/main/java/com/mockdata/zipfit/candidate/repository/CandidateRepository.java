package com.mockdata.zipfit.candidate.repository;

import com.mockdata.zipfit.candidate.entity.Candidate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
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

	/** 일조나 소음이 비어 있는 후보지 (LOC-04 이전에 추가됐거나 산출에 실패한 곳) */
	@Query("select c.id from Candidate c where c.location.sunlightGrade is null or c.location.noiseGrade is null"
		+ " order by c.id")
	List<Long> findIdsMissingSunlightOrNoise(Limit limit);

	@Modifying(flushAutomatically = true)
	@Query("delete from Candidate c where c.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);

	@Modifying(flushAutomatically = true)
	@Query("update Candidate c set c.addedBy = null where c.addedBy.id = :userId")
	int clearAddedBy(Long userId);
}
