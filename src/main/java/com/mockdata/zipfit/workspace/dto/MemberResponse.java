package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.entity.WorkspaceRole;
import java.time.Instant;

/** MEM-03 멤버 목록 항목. 멤버 변경 통지(MEMBER_JOINED)의 data로도 쓴다. */
public record MemberResponse(
	Long userId,
	String nickname,
	String profileImageUrl,
	WorkspaceRole role,
	Instant joinedAt
) {

	public static MemberResponse of(WorkspaceMember member) {
		return new MemberResponse(
			member.getUser().getId(), member.getUser().getNickname(), member.getUser().getProfileImageUrl(),
			member.getRole(), member.getJoinedAt()
		);
	}
}
