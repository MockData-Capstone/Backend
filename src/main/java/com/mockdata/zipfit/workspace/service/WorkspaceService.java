package com.mockdata.zipfit.workspace.service;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.workspace.dto.CreateWorkspaceRequest;
import com.mockdata.zipfit.workspace.dto.MemberProfile;
import com.mockdata.zipfit.workspace.dto.UpdateWorkspaceRequest;
import com.mockdata.zipfit.workspace.dto.WorkspaceResponse;
import com.mockdata.zipfit.workspace.dto.WorkspaceSummaryResponse;
import com.mockdata.zipfit.workspace.entity.Workspace;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import com.mockdata.zipfit.workspace.repository.InvitationRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceMemberRepository;
import com.mockdata.zipfit.workspace.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** WS-01~04 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository memberRepository;
	private final InvitationRepository invitationRepository;
	private final UserRepository userRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;

	/** WS-01 생성자를 호스트로 등록한다. */
	@Transactional
	public WorkspaceResponse create(Long userId, CreateWorkspaceRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		Workspace workspace = workspaceRepository.save(
			new Workspace(request.name().trim(), normalizeDescription(request.description()), Instant.now())
		);
		memberRepository.save(new WorkspaceMember(workspace, user, WorkspaceRole.HOST));
		return WorkspaceResponse.of(workspace, MemberProfile.of(user), 1, WorkspaceRole.HOST);
	}

	/** WS-02 내가 속한 Workspace 목록. 최근 활동순이다. */
	@Transactional(readOnly = true)
	public List<WorkspaceSummaryResponse> getMyWorkspaces(Long userId) {
		List<WorkspaceMember> memberships = memberRepository.findAllByUserId(userId);
		if (memberships.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> memberCounts = memberRepository.countByWorkspaceIds(
				memberships.stream().map(m -> m.getWorkspace().getId()).toList()
			).stream()
			.collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
		return memberships.stream()
			.map(m -> new WorkspaceSummaryResponse(
				m.getWorkspace().getId(),
				m.getWorkspace().getName(),
				memberCounts.getOrDefault(m.getWorkspace().getId(), 0L),
				m.getRole(),
				m.getWorkspace().getLastActivityAt()
			))
			.sorted(Comparator.comparing(WorkspaceSummaryResponse::lastActivityAt).reversed()
				.thenComparing(WorkspaceSummaryResponse::id, Comparator.reverseOrder()))
			.toList();
	}

	/** WS-02 상세 */
	@Transactional(readOnly = true)
	public WorkspaceResponse getWorkspace(Long workspaceId, Long userId) {
		WorkspaceMember me = workspaceAccess.requireMember(workspaceId, userId);
		return toResponse(me.getWorkspace(), me.getRole());
	}

	/** WS-03 전달된 필드만 바꾸고 접속 중인 멤버에게 알린다. */
	@Transactional
	public WorkspaceResponse update(Long workspaceId, Long userId, UpdateWorkspaceRequest request) {
		WorkspaceMember host = workspaceAccess.requireHost(workspaceId, userId);
		Workspace workspace = host.getWorkspace();
		if (request.name() != null) {
			workspace.rename(request.name().trim());
		}
		if (request.description() != null) {
			workspace.changeDescription(normalizeDescription(request.description()));
		}
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.WORKSPACE_UPDATED,
			new WorkspaceUpdated(workspaceId, workspace.getName(), workspace.getDescription())));
		return toResponse(workspace, host.getRole());
	}

	/** WS-04 하위 데이터와 함께 삭제한다. 복구할 수 없다. */
	@Transactional
	public void delete(Long workspaceId, Long userId) {
		workspaceAccess.requireHost(workspaceId, userId);
		deleteWithChildren(workspaceId);
	}

	/**
	 * 하위 데이터와 함께 지운다. 투어·미니게임·채팅 등 각 도메인은 {@link WorkspaceDeletingEvent}를 받아 자기 데이터를 정리한다.
	 * 회원 탈퇴로 혼자 남은 Workspace를 지울 때도 쓴다.
	 */
	@Transactional
	public void deleteWithChildren(Long workspaceId) {
		eventPublisher.publishEvent(new WorkspaceDeletingEvent(workspaceId));
		invitationRepository.deleteAllByWorkspaceId(workspaceId);
		// 이미 불러온 멤버(예: 탈퇴 처리 중인 회원의 멤버십)도 함께 지워지도록 벌크 삭제 대신 엔티티로 지운다.
		memberRepository.deleteAll(memberRepository.findAllByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId));
		workspaceRepository.deleteById(workspaceId);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, WorkspaceEventTypes.WORKSPACE_DELETED,
			new WorkspaceDeleted(workspaceId)));
	}

	private WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole myRole) {
		MemberProfile host = memberRepository.findByWorkspaceIdAndRole(workspace.getId(), WorkspaceRole.HOST)
			.map(member -> MemberProfile.of(member.getUser()))
			.orElse(null);
		return WorkspaceResponse.of(workspace, host, memberRepository.countByWorkspaceId(workspace.getId()), myRole);
	}

	/** 설명은 선택값이라 빈 문자열이면 지운다. */
	private static String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

	public record WorkspaceUpdated(Long workspaceId, String name, String description) {
	}

	public record WorkspaceDeleted(Long workspaceId) {
	}
}
