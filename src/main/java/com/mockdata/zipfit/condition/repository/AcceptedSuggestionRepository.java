package com.mockdata.zipfit.condition.repository;

import com.mockdata.zipfit.condition.entity.AcceptedSuggestion;
import com.mockdata.zipfit.condition.entity.SuggestionType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AcceptedSuggestionRepository extends JpaRepository<AcceptedSuggestion, Long> {

	List<AcceptedSuggestion> findAllByWorkspaceIdAndAnalysisId(Long workspaceId, Long analysisId);

	boolean existsByWorkspaceIdAndAnalysisIdAndType(Long workspaceId, Long analysisId, SuggestionType type);

	@Modifying(flushAutomatically = true)
	@Query("delete from AcceptedSuggestion a where a.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
