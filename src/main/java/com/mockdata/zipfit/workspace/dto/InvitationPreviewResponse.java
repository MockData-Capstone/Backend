package com.mockdata.zipfit.workspace.dto;

import java.time.Instant;

/** MEM-02 보조. 참여 확인 화면용이라 로그인 없이 조회한다. */
public record InvitationPreviewResponse(String workspaceName, String hostNickname, long memberCount, Instant expiresAt) {
}
