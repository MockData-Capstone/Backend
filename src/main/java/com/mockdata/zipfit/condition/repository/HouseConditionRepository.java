package com.mockdata.zipfit.condition.repository;

import com.mockdata.zipfit.condition.entity.HouseCondition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HouseConditionRepository extends JpaRepository<HouseCondition, Long> {

	Optional<HouseCondition> findByWorkspaceId(Long workspaceId);

	@Modifying(flushAutomatically = true)
	@Query("delete from HouseCondition c where c.workspace.id = :workspaceId")
	int deleteByWorkspaceId(Long workspaceId);
}
