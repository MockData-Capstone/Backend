package com.mockdata.zipfit.recommendation.service;

import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.recommendation.dto.AnalysisResponse;
import com.mockdata.zipfit.recommendation.dto.RecommendationsResponse;
import com.mockdata.zipfit.recommendation.entity.Analysis;
import com.mockdata.zipfit.recommendation.entity.AnalysisStatus;
import com.mockdata.zipfit.recommendation.entity.Recommendation;
import com.mockdata.zipfit.recommendation.repository.AnalysisRepository;
import com.mockdata.zipfit.recommendation.repository.RecommendationRepository;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** REC-01 AI 분석 요청, REC-02 분석 상태·추천 위치 조회 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

	public static final String RECOMMENDATION_STARTED = "RECOMMENDATION_STARTED";
	public static final String RECOMMENDATION_COMPLETED = "RECOMMENDATION_COMPLETED";
	public static final String RECOMMENDATION_FAILED = "RECOMMENDATION_FAILED";

	private final AnalysisRepository analysisRepository;
	private final RecommendationRepository recommendationRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ChatService chatService;
	private final ObjectProvider<CandidateLookup> candidateLookup;
	private final ApplicationEventPublisher eventPublisher;
	private final JsonMapper jsonMapper;

	/**
	 * REC-01 분석을 접수하고 바로 돌려준다. 분석은 커밋 뒤 따로 진행되고, 끝나면 「분석 진행 통지」로 알린다.
	 * 대화 기록이 없거나 이미 분석 중이면 시작하지 않는다 (FR-010).
	 */
	@Transactional
	public AnalysisResponse request(Long workspaceId, Long userId) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		if (!chatService.hasMessages(workspaceId)) {
			throw new BusinessException(ErrorCode.NO_CONVERSATION);
		}
		if (analysisRepository.existsByWorkspaceIdAndStatus(workspaceId, AnalysisStatus.IN_PROGRESS)) {
			throw new BusinessException(ErrorCode.ANALYSIS_IN_PROGRESS);
		}
		Analysis analysis;
		try {
			analysis = analysisRepository.saveAndFlush(new Analysis(member.getWorkspace()));
		} catch (DataIntegrityViolationException e) {
			// 같은 순간에 다른 멤버가 먼저 요청했다.
			throw new BusinessException(ErrorCode.ANALYSIS_IN_PROGRESS);
		}
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, RECOMMENDATION_STARTED,
			new AnalysisStarted(analysis.getId())));
		eventPublisher.publishEvent(new AnalysisRequestedEvent(analysis.getId(), workspaceId));
		return toResponse(analysis);
	}

	/** REC-02 가장 최근 분석의 상태와, 가장 최근에 완료된 분석의 추천 위치 */
	@Transactional(readOnly = true)
	public RecommendationsResponse getLatest(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		Optional<Analysis> latest = analysisRepository.findFirstByWorkspaceIdOrderByIdDesc(workspaceId);
		if (latest.isEmpty()) {
			return new RecommendationsResponse(null, List.of());
		}
		List<Recommendation> recommendations = analysisRepository
			.findFirstByWorkspaceIdAndStatusOrderByIdDesc(workspaceId, AnalysisStatus.COMPLETED)
			.map(completed -> recommendationRepository.findAllByAnalysisIdOrderByIdAsc(completed.getId()))
			.orElse(List.of());
		Map<Long, Long> candidateIds = candidateLookup.getIfAvailable(() -> (ws, ids) -> Map.of())
			.findCandidateIds(workspaceId, recommendations.stream().map(Recommendation::getId).toList());
		List<RecommendationsResponse.Item> items = recommendations.stream()
			.map(r -> new RecommendationsResponse.Item(
				r.getId(), r.getPlaceName(), r.getLatitude(), r.getLongitude(), r.getReason(), r.getKakaoPlaceId(),
				candidateIds.containsKey(r.getId()), candidateIds.get(r.getId())
			))
			.toList();
		return new RecommendationsResponse(toResponse(latest.get()), items);
	}

	/** 조건 확정(COND-01) 화면의 초기값: 가장 최근에 완료된 분석의 추출 조건 */
	@Transactional(readOnly = true)
	public Optional<ExtractedConditions> getLatestConditions(Long workspaceId) {
		return analysisRepository.findFirstByWorkspaceIdAndStatusOrderByIdDesc(workspaceId, AnalysisStatus.COMPLETED)
			.map(analysis -> readConditions(analysis.getConditionsJson()));
	}

	/** 후보지 추가(CAND-01)에 쓰는 추천 위치. 다른 Workspace의 추천이면 empty다. */
	@Transactional(readOnly = true)
	public Optional<Recommendation> findRecommendation(Long workspaceId, Long recommendationId) {
		return recommendationRepository.findWithAnalysisById(recommendationId)
			.filter(r -> r.getAnalysis().getWorkspace().getId().equals(workspaceId));
	}

	/** WS-04 Workspace 삭제 시 분석 결과와 추천 위치를 지운다. */
	@EventListener
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		recommendationRepository.deleteAllByWorkspaceId(event.workspaceId());
		analysisRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	AnalysisResponse toResponse(Analysis analysis) {
		return new AnalysisResponse(
			analysis.getId(),
			analysis.getStatus(),
			analysis.getFailure(),
			analysis.getFailure() == null ? null : analysis.getFailure().getMessage(),
			readConditions(analysis.getConditionsJson()),
			analysis.getRequestedAt(),
			analysis.getCompletedAt()
		);
	}

	private ExtractedConditions readConditions(String json) {
		return json == null ? null : jsonMapper.readValue(json, ExtractedConditions.class);
	}

	/** 커밋 뒤 분석을 시작하라는 내부 이벤트 */
	public record AnalysisRequestedEvent(Long analysisId, Long workspaceId) {
	}

	public record AnalysisStarted(Long analysisId) {
	}
}
