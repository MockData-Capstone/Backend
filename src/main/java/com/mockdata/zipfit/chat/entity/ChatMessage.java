package com.mockdata.zipfit.chat.entity;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 채팅 메시지 (CHAT-01). AI 분석의 입력이라 사용자가 수정·삭제할 수 없다.
 * 작성자가 탈퇴하면 작성자와 내용을 지우고 deleted로 남긴다 (AUTH-06, NFR-016).
 */
@Entity
@Table(
	name = "chat_message",
	uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "sender_id", "client_message_id"}),
	indexes = {@Index(columnList = "workspace_id, id"), @Index(columnList = "sender_id")}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	// 메시지 최대 길이 (제안, 기능명세서 7장 #25)
	public static final int CONTENT_MAX_LENGTH = 1000;
	public static final int CLIENT_MESSAGE_ID_MAX_LENGTH = 64;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false)
	private Workspace workspace;

	/** 탈퇴하면 null */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id")
	private User sender;

	/** 클라이언트가 만든 ID. 같은 ID로 다시 보내면 새로 저장하지 않는다. */
	@Column(name = "client_message_id", length = CLIENT_MESSAGE_ID_MAX_LENGTH)
	private String clientMessageId;

	/** 작성자가 탈퇴하면 null */
	@Column(length = CONTENT_MAX_LENGTH)
	private String content;

	@Column(nullable = false)
	private boolean deleted;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public ChatMessage(Workspace workspace, User sender, String clientMessageId, String content) {
		this.workspace = workspace;
		this.sender = sender;
		this.clientMessageId = clientMessageId;
		this.content = content;
	}
}
