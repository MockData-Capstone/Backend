package com.mockdata.zipfit.workspace.entity;

import com.mockdata.zipfit.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 친구들과 집을 함께 찾는 공간. 호스트는 {@link WorkspaceMember}의 역할로 정한다. */
@Entity
@Table(name = "workspace")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseTimeEntity {

	// 이름·설명 길이 제한 (추가 제안)
	public static final int NAME_MAX_LENGTH = 30;
	public static final int DESCRIPTION_MAX_LENGTH = 200;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(length = DESCRIPTION_MAX_LENGTH)
	private String description;

	/** 목록 정렬 기준. 멤버 입장, 채팅 등 활동이 있을 때 갱신한다. */
	@Column(nullable = false)
	private Instant lastActivityAt;

	public Workspace(String name, String description, Instant now) {
		this.name = name;
		this.description = description;
		this.lastActivityAt = now;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changeDescription(String description) {
		this.description = description;
	}

	public void touch(Instant now) {
		if (now.isAfter(lastActivityAt)) {
			this.lastActivityAt = now;
		}
	}
}
