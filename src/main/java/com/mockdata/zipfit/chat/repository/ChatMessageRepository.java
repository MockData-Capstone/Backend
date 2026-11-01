package com.mockdata.zipfit.chat.repository;

import com.mockdata.zipfit.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@EntityGraph(attributePaths = "sender")
	Optional<ChatMessage> findByWorkspaceIdAndSenderIdAndClientMessageId(
		Long workspaceId, Long senderId, String clientMessageId
	);

	/** 최신 메시지부터 */
	@EntityGraph(attributePaths = "sender")
	List<ChatMessage> findByWorkspaceIdOrderByIdDesc(Long workspaceId, Pageable pageable);

	/** before보다 이전 메시지를 최신부터 */
	@EntityGraph(attributePaths = "sender")
	List<ChatMessage> findByWorkspaceIdAndIdLessThanOrderByIdDesc(Long workspaceId, Long before, Pageable pageable);

	/** after보다 뒤 메시지를 오래된 것부터 */
	@EntityGraph(attributePaths = "sender")
	List<ChatMessage> findByWorkspaceIdAndIdGreaterThanOrderByIdAsc(Long workspaceId, Long after, Pageable pageable);

	/** AI 분석 입력: 탈퇴한 회원의 메시지를 뺀 최신 메시지부터 */
	List<ChatMessage> findByWorkspaceIdAndDeletedFalseOrderByIdDesc(Long workspaceId, Pageable pageable);

	boolean existsByWorkspaceId(Long workspaceId);

	@Modifying(flushAutomatically = true)
	@Query("delete from ChatMessage m where m.workspace.id = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);

	/** 탈퇴한 작성자의 메시지에서 작성자와 내용을 지운다. */
	@Modifying(flushAutomatically = true)
	@Query("update ChatMessage m set m.sender = null, m.content = null, m.deleted = true where m.sender.id = :senderId")
	int anonymizeBySenderId(Long senderId);
}
