package com.mockdata.zipfit.workspace.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.workspace.dto.CurrentInvitationResponse;
import com.mockdata.zipfit.workspace.dto.DelegateHostRequest;
import com.mockdata.zipfit.workspace.dto.InvitationResponse;
import com.mockdata.zipfit.workspace.dto.MemberResponse;
import com.mockdata.zipfit.workspace.dto.WorkspaceResponse;
import com.mockdata.zipfit.workspace.service.InvitationService;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Workspace 안의 멤버·초대 링크 (MEM-01, MEM-03~06) */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final InvitationService invitationService;
	private final WorkspaceService workspaceService;

	/** MEM-01 초대 링크 발급 (호스트). 이전 링크는 바로 무효가 된다. */
	@PostMapping("/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	public InvitationResponse issueInvitation(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return invitationService.issue(workspaceId, userId);
	}

	/** MEM-01 보조 현재 초대 링크 조회 (호스트). 없으면 invitation이 null이다. */
	@GetMapping("/invitations/current")
	public CurrentInvitationResponse getCurrentInvitation(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new CurrentInvitationResponse(invitationService.getCurrent(workspaceId, userId).orElse(null));
	}

	/** MEM-03 멤버 목록 조회 */
	@GetMapping("/members")
	public ListResponse<MemberResponse> getMembers(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(memberService.getMembers(workspaceId, userId));
	}

	/** MEM-04 호스트 위임. 바뀐 Workspace 상세를 돌려준다(내 역할은 MEMBER). */
	@PatchMapping("/host")
	public WorkspaceResponse delegateHost(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody DelegateHostRequest request
	) {
		memberService.delegateHost(workspaceId, userId, request.userId());
		return workspaceService.getWorkspace(workspaceId, userId);
	}

	/** MEM-06 Workspace 나가기 */
	@DeleteMapping("/members/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		memberService.leave(workspaceId, userId);
	}

	/** MEM-05 멤버 내보내기 (호스트) */
	@DeleteMapping("/members/{targetUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void kick(@CurrentUser Long userId, @PathVariable Long workspaceId, @PathVariable Long targetUserId) {
		memberService.kick(workspaceId, userId, targetUserId);
	}
}
