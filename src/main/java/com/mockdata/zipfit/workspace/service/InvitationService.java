package com.mockdata.zipfit.workspace.service;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.SecureTokens;
import com.mockdata.zipfit.workspace.dto.AcceptInvitationResponse;
import com.mockdata.zipfit.workspace.dto.InvitationPreviewResponse;
import com.mockdata.zipfit.workspace.dto.InvitationResponse;
import com.mockdata.zipfit.workspace.dto.MemberResponse;
import com.mockdata.zipfit.workspace.entity.Invitation;
import com.mockdata.zipfit.workspace.entity.Workspace;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import com.mockdata.zipfit.workspace.repository.InvitationRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MEM-01 초대 링크 발급, MEM-02 초대 링크로 참여 */
@Service
public class InvitationService {

	private final InvitationRepository invitationRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;
	private final Duration expiration;

	public InvitationService(
		InvitationRepository invitationRepository,
		WorkspaceMemberRepository memberRepository,
		UserRepository userRepository,
		WorkspaceAccess workspaceAccess,
		ApplicationEventPublisher eventPublisher,
		@Value("${app.invitation.expiration}") Duration expiration
	) {
		this.invitationRepository = invitationRepository;
		this.memberRepository = memberRepository;
		this.userRepository = userRepository;
		this.workspaceAccess = workspaceAccess;
		this.eventPublisher = eventPublisher;
		this.expiration = expiration;
	}

	/** MEM-01 Workspace당 유효한 링크는 1개다. 다시 발급하면 이전 링크는 바로 무효가 된다. (추가 제안) */
	@Transactional
	public InvitationResponse issue(Long workspaceId, Long userId) {
		WorkspaceMember host = workspaceAccess.requireHost(workspaceId, userId);
		invitationRepository.deleteAllByWorkspaceId(workspaceId);
		Invitation invitation = invitationRepository.save(
			new Invitation(host.getWorkspace(), SecureTokens.generate(), Instant.now().plus(expiration))
		);
		return InvitationResponse.of(invitation);
	}

	/** MEM-01 보조. 유효한 링크가 없으면 empty다. */
	@Transactional(readOnly = true)
	public Optional<InvitationResponse> getCurrent(Long workspaceId, Long userId) {
		workspaceAccess.requireHost(workspaceId, userId);
		return invitationRepository.findFirstByWorkspaceIdAndExpiresAtAfterOrderByIdDesc(workspaceId, Instant.now())
			.map(InvitationResponse::of);
	}

	/** MEM-02 보조. 로그인하지 않아도 조회할 수 있다. 만료·무효 링크면 410이다. */
	@Transactional(readOnly = true)
	public InvitationPreviewResponse preview(String token) {
		Invitation invitation = findValid(token);
		Long workspaceId = invitation.getWorkspace().getId();
		String hostNickname = memberRepository.findByWorkspaceIdAndRole(workspaceId, WorkspaceRole.HOST)
			.map(host -> host.getUser().getNickname())
			.orElse(null);
		return new InvitationPreviewResponse(
			invitation.getWorkspace().getName(), hostNickname, memberRepository.countByWorkspaceId(workspaceId),
			invitation.getExpiresAt()
		);
	}

	/** MEM-02 멤버로 등록한다. 이미 멤버면 등록하지 않고 같은 Workspace ID를 돌려준다. */
	@Transactional
	public AcceptInvitationResponse accept(String token, Long userId) {
		Invitation invitation = findValid(token);
		Workspace workspace = invitation.getWorkspace();
		if (memberRepository.existsByWorkspaceIdAndUserId(workspace.getId(), userId)) {
			return new AcceptInvitationResponse(workspace.getId(), true);
		}
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		WorkspaceMember member = memberRepository.saveAndFlush(new WorkspaceMember(workspace, user, WorkspaceRole.MEMBER));
		workspace.touch(Instant.now());
		eventPublisher.publishEvent(new WorkspaceEvent(workspace.getId(), WorkspaceEventTypes.MEMBER_JOINED,
			MemberResponse.of(member)));
		return new AcceptInvitationResponse(workspace.getId(), false);
	}

	private Invitation findValid(String token) {
		return invitationRepository.findByToken(token)
			.filter(invitation -> !invitation.isExpired(Instant.now()))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_EXPIRED));
	}
}
