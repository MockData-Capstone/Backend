package com.mockdata.zipfit.condition.service;

import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.condition.dto.ConditionRequest;
import com.mockdata.zipfit.condition.dto.ConditionResponse;
import com.mockdata.zipfit.condition.dto.ConditionView;
import com.mockdata.zipfit.condition.entity.HouseCondition;
import com.mockdata.zipfit.condition.repository.HouseConditionRepository;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COND-01~03 확정 조건 */
@Service
@RequiredArgsConstructor
public class ConditionService {

	public static final String CONDITION_UPDATED = "CONDITION_UPDATED";

	private final HouseConditionRepository conditionRepository;
	private final RecommendationService recommendationService;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;

	/** COND-02 확정 조건과, 화면을 미리 채울 초기값 */
	@Transactional(readOnly = true)
	public ConditionView get(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		ConditionResponse condition = conditionRepository.findByWorkspaceId(workspaceId)
			.map(ConditionResponse::of)
			.orElse(null);
		ConditionRequest draft = recommendationService.getLatestConditions(workspaceId)
			.map(ConditionService::toDraft)
			.orElse(null);
		return new ConditionView(condition, draft);
	}

	/** COND-01 조건 확정. Workspace당 1건이며 이미 있으면 수정(COND-03)을 쓴다 (제안). */
	@Transactional
	public ConditionResponse confirm(Long workspaceId, Long userId, ConditionRequest request) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		validateRanges(request);
		if (conditionRepository.findByWorkspaceId(workspaceId).isPresent()) {
			throw new BusinessException(ErrorCode.CONDITION_ALREADY_EXISTS);
		}
		HouseCondition condition;
		try {
			condition = conditionRepository.saveAndFlush(new HouseCondition(member.getWorkspace(), request, userId,
				member.getUser().getNickname(), Instant.now()));
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.CONDITION_ALREADY_EXISTS);
		}
		return publish(workspaceId, condition);
	}

	/** COND-03 화면의 값 전체로 바꾼다. 동시에 수정하면 마지막 저장을 반영하고, 누가 바꿨는지 알린다 (제안). */
	@Transactional
	public ConditionResponse update(Long workspaceId, Long userId, ConditionRequest request) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		validateRanges(request);
		HouseCondition condition = conditionRepository.findByWorkspaceId(workspaceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CONDITION_NOT_FOUND));
		condition.replace(request, userId, member.getUser().getNickname(), Instant.now());
		return publish(workspaceId, condition);
	}

	/** 매물 검색 링크(LINK-01)에 쓰는 확정 조건 */
	@Transactional(readOnly = true)
	public Optional<HouseCondition> find(Long workspaceId) {
		return conditionRepository.findByWorkspaceId(workspaceId);
	}

	/** WS-04 */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		conditionRepository.deleteByWorkspaceId(event.workspaceId());
	}

	private ConditionResponse publish(Long workspaceId, HouseCondition condition) {
		ConditionResponse response = ConditionResponse.of(condition);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, CONDITION_UPDATED, response));
		return response;
	}

	/** 범위의 최솟값이 최댓값보다 크면 저장할 수 없다. */
	private static void validateRanges(ConditionRequest r) {
		checkRange("보증금", r.depositMin(), r.depositMax());
		checkRange("월세", r.monthlyRentMin(), r.monthlyRentMax());
		checkRange("면적", r.areaMin(), r.areaMax());
	}

	private static <T extends Comparable<T>> void checkRange(String label, T min, T max) {
		if (min != null && max != null && min.compareTo(max) > 0) {
			throw new BusinessException(ErrorCode.INVALID_RANGE, label + " 최솟값이 최댓값보다 클 수 없습니다.");
		}
	}

	private static ConditionRequest toDraft(ExtractedConditions c) {
		return new ConditionRequest(c.depositMin(), c.depositMax(), c.monthlyRentMin(), c.monthlyRentMax(), c.roomType(),
			c.areaMin(), c.areaMax(), c.avoidSlope(), c.needVehicleAccess());
	}
}
