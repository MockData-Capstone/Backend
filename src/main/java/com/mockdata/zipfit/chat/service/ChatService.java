package com.mockdata.zipfit.chat.service;

import com.mockdata.zipfit.chat.dto.ChatMessageResponse;
import com.mockdata.zipfit.chat.entity.ChatMessage;
import com.mockdata.zipfit.chat.repository.ChatMessageRepository;
import com.mockdata.zipfit.common.dto.CursorPage;
import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CHAT-01 메시지 전송, CHAT-02 대화 기록 조회 */
@Service
@RequiredArgsConstructor
public class ChatService {

	public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
	public static final int DEFAULT_PAGE_SIZE = 50;
	public static final int MAX_PAGE_SIZE = 100;

	private final ChatMessageRepository messageRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;

	/**
	 * 저장한 뒤 커밋되면 CHAT_MESSAGE로 멤버 전원에게 보낸다.
	 * 같은 clientMessageId로 다시 보내면 새로 저장하지 않고 기존 메시지를 돌려준다(created = false).
	 */
	@Transactional
	public SendResult send(Long workspaceId, Long userId, String clientMessageId, String content) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		if (clientMessageId == null || clientMessageId.isBlank()
			|| clientMessageId.length() > ChatMessage.CLIENT_MESSAGE_ID_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_SOCKET_MESSAGE);
		}
		Optional<ChatMessage> existing = messageRepository.findByWorkspaceIdAndSenderIdAndClientMessageId(
			workspaceId, userId, clientMessageId
		);
		if (existing.isPresent()) {
			return new SendResult(ChatMessageResponse.of(existing.get()), false);
		}
		if (content == null || content.isBlank()) {
			throw new BusinessException(ErrorCode.EMPTY_MESSAGE);
		}
		if (content.length() > ChatMessage.CONTENT_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.MESSAGE_TOO_LONG);
		}

		ChatMessage message = messageRepository.saveAndFlush(
			new ChatMessage(member.getWorkspace(), member.getUser(), clientMessageId, content)
		);
		member.getWorkspace().touch(Instant.now());
		ChatMessageResponse response = ChatMessageResponse.of(message);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, CHAT_MESSAGE, response));
		return new SendResult(response, true);
	}

	/**
	 * CHAT-02 시간순(오래된 것 → 최신) 커서 페이지.
	 * <ul>
	 *   <li>커서 없음: 최신 size개. nextCursor는 더 이전 기록을 받을 before 값이다.</li>
	 *   <li>before: 그 메시지보다 이전 size개. nextCursor는 다음 before 값이다.</li>
	 *   <li>after: 재연결 뒤 놓친 메시지 size개. nextCursor는 다음 after 값이다.</li>
	 * </ul>
	 */
	@Transactional(readOnly = true)
	public CursorPage<ChatMessageResponse> getMessages(Long workspaceId, Long userId, Long before, Long after, int size) {
		workspaceAccess.requireMember(workspaceId, userId);
		if (before != null && after != null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "before와 after는 함께 보낼 수 없습니다.");
		}
		PageRequest limit = PageRequest.ofSize(size + 1);

		if (after != null) {
			List<ChatMessage> rows = messageRepository.findByWorkspaceIdAndIdGreaterThanOrderByIdAsc(workspaceId, after, limit);
			boolean hasNext = rows.size() > size;
			List<ChatMessage> page = hasNext ? rows.subList(0, size) : rows;
			return toPage(page, hasNext, hasNext ? page.getLast().getId() : null);
		}

		List<ChatMessage> rows = before == null
			? messageRepository.findByWorkspaceIdOrderByIdDesc(workspaceId, limit)
			: messageRepository.findByWorkspaceIdAndIdLessThanOrderByIdDesc(workspaceId, before, limit);
		boolean hasNext = rows.size() > size;
		List<ChatMessage> page = new ArrayList<>(hasNext ? rows.subList(0, size) : rows);
		Collections.reverse(page);
		return toPage(page, hasNext, hasNext ? page.getFirst().getId() : null);
	}

	/** AI 분석(REC-01)에서 대화 기록이 있는지 확인할 때 쓴다. */
	@Transactional(readOnly = true)
	public boolean hasMessages(Long workspaceId) {
		return messageRepository.existsByWorkspaceId(workspaceId);
	}

	/** AI 분석(REC-01)의 입력. 최근 limit개를 오래된 것부터 돌려준다. 탈퇴한 회원의 메시지는 뺀다. */
	@Transactional(readOnly = true)
	public List<ConversationLine> getConversation(Long workspaceId, int limit) {
		List<ConversationLine> lines = new ArrayList<>(
			messageRepository.findByWorkspaceIdAndDeletedFalseOrderByIdDesc(workspaceId, PageRequest.ofSize(limit)).stream()
				.map(m -> new ConversationLine(m.getSender().getId(), m.getContent(), m.getCreatedAt()))
				.toList()
		);
		Collections.reverse(lines);
		return lines;
	}

	/** WS-04 Workspace 삭제 시 대화 원본을 파기한다 (NFR-016). */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		messageRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	/** AUTH-06 탈퇴한 회원이 쓴 메시지의 내용을 지운다 (NFR-016). */
	@EventListener
	@Transactional
	public void onUserWithdrawing(UserWithdrawingEvent event) {
		messageRepository.anonymizeBySenderId(event.userId());
	}

	private static CursorPage<ChatMessageResponse> toPage(List<ChatMessage> page, boolean hasNext, Long nextCursor) {
		return new CursorPage<>(
			page.stream().map(ChatMessageResponse::of).toList(),
			nextCursor == null ? null : nextCursor.toString(),
			hasNext
		);
	}

	public record SendResult(ChatMessageResponse message, boolean created) {
	}

	public record ConversationLine(Long senderId, String content, Instant sentAt) {
	}
}
