package com.mockdata.zipfit.vote.repository;

import com.mockdata.zipfit.vote.entity.Vote;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VoteRepository extends JpaRepository<Vote, Long> {

	Optional<Vote> findByCandidateIdAndUserId(Long candidateId, Long userId);

	boolean existsByCandidateIdAndUserId(Long candidateId, Long userId);

	@EntityGraph(attributePaths = "user")
	List<Vote> findAllByCandidateIdInOrderByIdAsc(Collection<Long> candidateIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from Vote v where v.candidate.id = :candidateId")
	int deleteAllByCandidateId(Long candidateId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Vote v where v.user.id = :userId")
	int deleteAllByUserId(Long userId);

	/** 나가거나 내보내진 멤버의 표를 집계에서 뺀다. */
	@Modifying(flushAutomatically = true)
	@Query("delete from Vote v where v.user.id = :userId"
		+ " and v.candidate.id in (select c.id from Candidate c where c.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceIdAndUserId(Long workspaceId, Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Vote v where v.candidate.id in (select c.id from Candidate c where c.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceId(Long workspaceId);
}
