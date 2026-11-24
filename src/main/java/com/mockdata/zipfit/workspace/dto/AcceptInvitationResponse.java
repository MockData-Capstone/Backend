package com.mockdata.zipfit.workspace.dto;

/**
 * MEM-02 초대 수락
 *
 * @param alreadyMember 이미 멤버였으면 true (등록하지 않고 바로 이동)
 */
public record AcceptInvitationResponse(Long workspaceId, boolean alreadyMember) {
}
