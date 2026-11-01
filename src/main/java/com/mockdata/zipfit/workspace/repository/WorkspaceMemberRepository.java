package com.mockdata.zipfit.workspace.repository;

import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

	Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);

	boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

	@EntityGraph(attributePaths = "user")
	Optional<WorkspaceMember> findByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);

	@EntityGraph(attributePaths = "user")
	List<WorkspaceMember> findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(Long workspaceId);

	@EntityGraph(attributePaths = "workspace")
	List<WorkspaceMember> findAllByUserId(Long userId);

	@Query("select m.user.id from WorkspaceMember m where m.workspace.id = :workspaceId")
	List<Long> findUserIdsByWorkspaceId(Long workspaceId);

	long countByWorkspaceId(Long workspaceId);

	/** [workspaceId, 멤버 수] 목록 */
	@Query("select m.workspace.id, count(m) from WorkspaceMember m where m.workspace.id in :workspaceIds group by m.workspace.id")
	List<Object[]> countByWorkspaceIds(Collection<Long> workspaceIds);
}
