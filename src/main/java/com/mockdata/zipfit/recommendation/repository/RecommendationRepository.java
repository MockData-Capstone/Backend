package com.mockdata.zipfit.recommendation.repository;

import com.mockdata.zipfit.recommendation.entity.Recommendation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

	List<Recommendation> findAllByAnalysisIdOrderByIdAsc(Long analysisId);

	/** 후보지 추가(CAND-01) 때 Workspace를 확인하려고 분석까지 함께 읽는다. */
	@EntityGraph(attributePaths = "analysis")
	Optional<Recommendation> findWithAnalysisById(Long id);

	@Modifying(flushAutomatically = true)
	@Query("delete from Recommendation r where r.analysis.id in (select a.id from Analysis a where a.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceId(Long workspaceId);
}
