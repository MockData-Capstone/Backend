package com.mockdata.zipfit.chat.dto;

/** CHAT_SEND의 data. clientMessageId는 클라이언트가 만든 고유 값(예: UUID)이다. */
public record ChatSendRequest(String clientMessageId, String content) {
}
