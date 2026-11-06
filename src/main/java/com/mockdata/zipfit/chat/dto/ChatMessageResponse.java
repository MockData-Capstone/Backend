package com.mockdata.zipfit.chat.dto;

import com.mockdata.zipfit.chat.entity.ChatMessage;
import com.mockdata.zipfit.workspace.dto.MemberProfile;
import java.time.Instant;

/**
 * 채팅 메시지. 기록 조회의 항목이자 CHAT_MESSAGE의 data다.
 *
 * @param sender  작성자가 탈퇴했으면 null
 * @param content 작성자가 탈퇴했으면 null
 * @param deleted true면 "탈퇴한 사용자의 메시지입니다"로 표시한다 (AUTH-06)
 */
public record ChatMessageResponse(
	Long messageId,
	Long workspaceId,
	String clientMessageId,
	MemberProfile sender,
	String content,
	boolean deleted,
	Instant createdAt
) {

	public static ChatMessageResponse of(ChatMessage message) {
		return new ChatMessageResponse(
			message.getId(),
			message.getWorkspace().getId(),
			message.getClientMessageId(),
			message.getSender() == null ? null : MemberProfile.of(message.getSender()),
			message.getContent(),
			message.isDeleted(),
			message.getCreatedAt()
		);
	}
}
