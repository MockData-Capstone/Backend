package com.mockdata.zipfit.workspace.service;

import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.workspace.dto.MemberResponse;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import com.mockdata.zipfit.workspace.repository.InvitationRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MEM-03~06 멤버 관리.
 * TODO(VOTE, TOUR): 나가거나 내보낸 멤버의 투표를 집계에서 빼고, 진행 중인 투어에서 제외한다. 채팅은 유지한다.
 */
@Service
@RequiredArgsConstructor
public class MemberService {

	private final WorkspaceMemberRepository memberRepository;
	private final InvitationRepository invitationRepository;
	private final WorkspaceAccess workspaceAccess;
	private final WorkspaceService workspaceService;
	private final ApplicationEventPublisher eventPublisher;

	/** MEM-03 참여순 */
	@Transactional(readOnly = true)
	public List<MemberResponse> getMembers(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return memberRepository.findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId).stream()
			.map(MemberResponse::of)
			.toList();
	}

	/** MEM-04 호스트는 항상 1명이다. 기존 호스트는 일반 멤버가 된다. */
	@Transactional
	public void delegateHost(Long workspaceId, Long userId, Long targetUserId) {
		WorkspaceMember host = workspaceAccess.requireHost(workspaceId, userId);
		if (userId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
		}
		WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		host.changeRole(WorkspaceRole.MEMBER);
		target.changeRole(WorkspaceRole.HOST);
		host.getWorkspace().touch(Instant.now());
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.HOST_CHANGED,
			new HostChanged(userId, targetUserId)));
	}

	/**
	 * MEM-05 대상의 멤버 자격을 없앤다.
	 * 내보낸 회원이 옛 링크로 다시 들어오지 못하게 그전에 발급한 초대 링크도 모두 무효로 한다. (제안)
	 */
	@Transactional
	public void kick(Long workspaceId, Long userId, Long targetUserId) {
		workspaceAccess.requireHost(workspaceId, userId);
		if (userId.equals(targetUserId)) {
			throw new BusinessException(ErrorCode.CANNOT_KICK_HOST);
		}
		WorkspaceMember target = memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		memberRepository.delete(target);
		invitationRepository.deleteAllByWorkspaceId(workspaceId);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.MEMBER_KICKED,
			new MemberRemoved(targetUserId)));
	}

	/** MEM-06 호스트는 위임한 뒤에만 나갈 수 있고, 혼자 남았으면 Workspace 삭제로 안내한다. */
	@Transactional
	public void leave(Long workspaceId, Long userId) {
		WorkspaceMember me = workspaceAccess.requireMember(workspaceId, userId);
		if (me.isHost()) {
			boolean alone = memberRepository.countByWorkspaceId(workspaceId) == 1;
			throw new BusinessException(alone ? ErrorCode.LAST_MEMBER_CANNOT_LEAVE : ErrorCode.HOST_CANNOT_LEAVE);
		}
		removeMember(me);
	}

	/**
	 * AUTH-06 회원 탈퇴.
	 * 다른 멤버가 있는 Workspace의 호스트면 탈퇴를 막고(위임 후 탈퇴), 혼자 남은 Workspace는 삭제하고, 나머지에서는 나간다.
	 * 막아야 하면 아무것도 바꾸기 전에 예외를 던진다.
	 */
	@EventListener
	@Transactional
	public void onUserWithdrawing(UserWithdrawingEvent event) {
		List<WorkspaceMember> memberships = memberRepository.findAllByUserId(event.userId());
		for (WorkspaceMember membership : memberships) {
			if (membership.isHost() && memberRepository.countByWorkspaceId(membership.getWorkspace().getId()) > 1) {
				throw new BusinessException(ErrorCode.HOST_DELEGATION_REQUIRED);
			}
		}
		for (WorkspaceMember membership : memberships) {
			if (membership.isHost()) {
				workspaceService.deleteWithChildren(membership.getWorkspace().getId());
			} else {
				removeMember(membership);
			}
		}
	}

	private void removeMember(WorkspaceMember member) {
		Long workspaceId = member.getWorkspace().getId();
		Long userId = member.getUser().getId();
		memberRepository.delete(member);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.MEMBER_LEFT,
			new MemberRemoved(userId)));
	}

	public record HostChanged(Long previousHostUserId, Long newHostUserId) {
	}

	public record MemberRemoved(Long userId) {
	}
}
