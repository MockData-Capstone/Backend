package com.mockdata.zipfit.workspace.repository;

import com.mockdata.zipfit.workspace.entity.Invitation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

	@EntityGraph(attributePaths = "workspace")
	Optional<Invitation> findByToken(String token);

	Optional<Invitation> findFirstByWorkspaceIdAndExpiresAtAfterOrderByIdDesc(Long workspaceId, Instant now);

	@Modifying(flushAutomatically = true)
	@Query("delete from Invitation i where i.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
