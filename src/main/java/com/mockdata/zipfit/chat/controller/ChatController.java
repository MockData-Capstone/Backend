package com.mockdata.zipfit.chat.controller;

import com.mockdata.zipfit.chat.dto.ChatMessageResponse;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.common.dto.CursorPage;
import com.mockdata.zipfit.common.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 메시지 전송은 Workspace 소켓의 CHAT_SEND로 한다. */
@RestController
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;

	/** CHAT-02 대화 기록 조회. before로 이전 기록을, after로 재연결 뒤 놓친 메시지를 받는다. */
	@GetMapping("/api/workspaces/{workspaceId}/messages")
	public CursorPage<ChatMessageResponse> getMessages(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@RequestParam(required = false) Long before,
		@RequestParam(required = false) Long after,
		@RequestParam(defaultValue = "" + ChatService.DEFAULT_PAGE_SIZE) @Min(1) @Max(ChatService.MAX_PAGE_SIZE) int size
	) {
		return chatService.getMessages(workspaceId, userId, before, after, size);
	}
}
