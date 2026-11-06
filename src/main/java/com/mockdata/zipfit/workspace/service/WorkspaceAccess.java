package com.mockdata.zipfit.workspace.service;

import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace 하위 기능의 접근 제어 (COM-01).
 * 없거나 삭제된 Workspace는 404, 멤버가 아니면 403, 호스트 전용 기능에 호스트가 아니면 403이다.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccess {

	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository memberRepository;

	@Transactional(readOnly = true)
	public WorkspaceMember requireMember(Long workspaceId, Long userId) {
		if (!workspaceRepository.existsById(workspaceId)) {
			throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);
		}
		return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER));
	}

	@Transactional(readOnly = true)
	public WorkspaceMember requireHost(Long workspaceId, Long userId) {
		WorkspaceMember member = requireMember(workspaceId, userId);
		if (!member.isHost()) {
			throw new BusinessException(ErrorCode.NOT_WORKSPACE_HOST);
		}
		return member;
	}

	@Transactional(readOnly = true)
	public boolean isMember(Long workspaceId, Long userId) {
		return memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
	}
}
