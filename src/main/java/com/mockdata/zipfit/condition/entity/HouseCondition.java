package com.mockdata.zipfit.condition.entity;

import com.mockdata.zipfit.ai.dto.RoomType;
import com.mockdata.zipfit.condition.dto.ConditionRequest;
import com.mockdata.zipfit.workspace.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 확정 조건 (COND-01). Workspace당 1건이고 모든 멤버가 수정할 수 있다 (제안). 삭제는 제공하지 않는다.
 * 지역은 저장하지 않고 후보지에서 가져온다. 금액 단위는 만원, 면적 단위는 ㎡다.
 */
@Entity
@Table(name = "house_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseCondition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workspace_id", nullable = false, unique = true)
	private Workspace workspace;

	private Integer depositMin;
	private Integer depositMax;
	private Integer monthlyRentMin;
	private Integer monthlyRentMax;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private RoomType roomType;

	private Double areaMin;
	private Double areaMax;

	/** 조건 제안(REC-03)으로 추가할 수 있는 조건: 오르막 회피, 차량 진입 필요 */
	private Boolean avoidSlope;
	private Boolean needVehicleAccess;

	/** 마지막으로 수정한 멤버. 탈퇴해도 남도록 ID와 닉네임으로 기록한다. */
	@Column(nullable = false)
	private Long lastModifiedByUserId;

	@Column(nullable = false, length = 20)
	private String lastModifiedByNickname;

	@Column(nullable = false)
	private Instant updatedAt;

	public HouseCondition(Workspace workspace, ConditionRequest request, Long userId, String nickname, Instant now) {
		this.workspace = workspace;
		replace(request, userId, nickname, now);
	}

	/** 동시에 수정하면 마지막 저장을 반영한다 (제안). */
	public void replace(ConditionRequest request, Long userId, String nickname, Instant now) {
		this.depositMin = request.depositMin();
		this.depositMax = request.depositMax();
		this.monthlyRentMin = request.monthlyRentMin();
		this.monthlyRentMax = request.monthlyRentMax();
		this.roomType = request.roomType();
		this.areaMin = request.areaMin();
		this.areaMax = request.areaMax();
		this.avoidSlope = request.avoidSlope();
		this.needVehicleAccess = request.needVehicleAccess();
		this.lastModifiedByUserId = userId;
		this.lastModifiedByNickname = nickname;
		this.updatedAt = now;
	}
}
