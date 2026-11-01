package com.mockdata.zipfit.ai.dto;

import java.time.Instant;
import java.util.List;

/** POST /api/ai/recommendations 요청. messages는 오래된 것부터다. */
public record AiRecommendationRequest(Long workspaceId, List<Message> messages) {

	/** speaker는 회원 ID 대신 쓰는 익명 이름이다 (예: "멤버1"). 탈퇴한 회원의 메시지는 보내지 않는다. */
	public record Message(String speaker, String content, Instant sentAt) {
	}
}
