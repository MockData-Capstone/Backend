package com.mockdata.zipfit.workspace.controller;

import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.workspace.dto.AcceptInvitationResponse;
import com.mockdata.zipfit.workspace.dto.InvitationPreviewResponse;
import com.mockdata.zipfit.workspace.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MEM-02 초대 링크로 참여 */
@RestController
@RequestMapping("/api/invitations/{token}")
@RequiredArgsConstructor
public class InvitationController {

	private final InvitationService invitationService;

	/** MEM-02 보조 초대 정보 조회. 로그인하지 않아도 된다. 만료·무효 링크면 410이다. */
	@GetMapping
	public InvitationPreviewResponse preview(@PathVariable String token) {
		return invitationService.preview(token);
	}

	/** MEM-02 초대 수락. 이미 멤버면 등록하지 않고 같은 workspaceId를 돌려준다. */
	@PostMapping("/accept")
	public AcceptInvitationResponse accept(@CurrentUser Long userId, @PathVariable String token) {
		return invitationService.accept(token, userId);
	}
}
