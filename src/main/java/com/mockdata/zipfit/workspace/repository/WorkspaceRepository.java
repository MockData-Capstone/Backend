package com.mockdata.zipfit.workspace.repository;

import com.mockdata.zipfit.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
