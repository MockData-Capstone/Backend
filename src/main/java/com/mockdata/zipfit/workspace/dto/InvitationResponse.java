package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.Invitation;
import java.time.Instant;

/** MEM-01 초대 링크. 프론트는 token으로 초대 링크 주소를 만든다. */
public record InvitationResponse(String token, Instant expiresAt) {

	public static InvitationResponse of(Invitation invitation) {
		return new InvitationResponse(invitation.getToken(), invitation.getExpiresAt());
	}
}
