package com.mockdata.zipfit.candidate.service;

import com.mockdata.zipfit.ai.AiClient;
import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.ai.dto.SlopeRequest;
import com.mockdata.zipfit.ai.dto.SlopeResponse;
import com.mockdata.zipfit.ai.dto.VehicleAccessRequest;
import com.mockdata.zipfit.ai.dto.VehicleAccessResponse;
import com.mockdata.zipfit.candidate.dto.CandidateResponse;
import com.mockdata.zipfit.candidate.dto.VoteSummary;
import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.entity.LocationInfo;
import com.mockdata.zipfit.candidate.repository.CandidateRepository;
import com.mockdata.zipfit.common.event.CandidateRemovingEvent;
import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.recommendation.entity.Recommendation;
import com.mockdata.zipfit.recommendation.service.CandidateLookup;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** CAND-01~04 후보지, LOC-01~03 입지 정보 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateService implements CandidateLookup {

	public static final String CANDIDATE_ADDED = "CANDIDATE_ADDED";
	public static final String CANDIDATE_UPDATED = "CANDIDATE_UPDATED";
	public static final String CANDIDATE_REMOVED = "CANDIDATE_REMOVED";

	/** 투표 등 후보지를 참조하는 데이터가 먼저 지워지도록 Workspace 삭제 처리 순서를 뒤로 둔다. */
	static final int DELETE_ORDER = 100;

	private final CandidateRepository candidateRepository;
	private final RecommendationService recommendationService;
	private final WorkspaceAccess workspaceAccess;
	private final AiClient aiClient;
	private final ObjectProvider<VoteSummaryProvider> voteSummaryProvider;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	/**
	 * CAND-01 추천 위치를 후보지로 추가하고 입지 정보(경사도·진입성)를 산출한다.
	 * 입지 분석은 AI 서버를 부르므로 트랜잭션 밖에서 하고, 실패한 항목은 비워 둔다("정보 없음").
	 */
	public CandidateResponse add(Long workspaceId, Long userId, Long recommendationId) {
		workspaceAccess.requireMember(workspaceId, userId);
		Recommendation recommendation = recommendationService.findRecommendation(workspaceId, recommendationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
		if (!recommendation.hasCoordinates()) {
			throw new BusinessException(ErrorCode.NO_COORDINATES);
		}
		if (candidateRepository.existsByWorkspaceIdAndLatitudeAndLongitude(
			workspaceId, recommendation.getLatitude(), recommendation.getLongitude())) {
			throw new BusinessException(ErrorCode.DUPLICATE_CANDIDATE);
		}
		LocationInfo location = analyzeLocation(workspaceId, recommendation.getLatitude(), recommendation.getLongitude());

		try {
			return transactionTemplate.execute(status -> {
				WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
				Candidate saved = candidateRepository.saveAndFlush(new Candidate(
					member.getWorkspace(), recommendation.getId(), recommendation.getPlaceName(),
					recommendation.getLatitude(), recommendation.getLongitude(), recommendation.getKakaoPlaceId(),
					member.getUser(), location
				));
				CandidateResponse added = CandidateResponse.of(saved, VoteSummary.EMPTY);
				eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, CANDIDATE_ADDED, added));
				return added;
			});
		} catch (DataIntegrityViolationException e) {
			// 입지 분석 중에 다른 멤버가 같은 위치를 먼저 추가했다.
			throw new BusinessException(ErrorCode.DUPLICATE_CANDIDATE);
		}
	}

	/** CAND-02 목록: 이름, 위치, 입지 정보 요약, 투표 집계 요약 */
	@Transactional(readOnly = true)
	public List<CandidateResponse> getCandidates(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		List<Candidate> candidates = candidateRepository.findAllByWorkspaceIdOrderByIdAsc(workspaceId);
		Map<Long, VoteSummary> votes = summarizeVotes(candidates.stream().map(Candidate::getId).toList(), userId);
		return candidates.stream()
			.map(c -> CandidateResponse.of(c, votes.getOrDefault(c.getId(), VoteSummary.EMPTY)))
			.toList();
	}

	/** CAND-02 상세 (입지 정보는 LOC-03) */
	@Transactional(readOnly = true)
	public CandidateResponse getCandidate(Long candidateId, Long userId) {
		Candidate candidate = findMemberCandidate(candidateId, userId);
		return CandidateResponse.of(candidate, summarizeVotes(List.of(candidateId), userId)
			.getOrDefault(candidateId, VoteSummary.EMPTY));
	}

	/** CAND-04 이름 수정 */
	@Transactional
	public CandidateResponse rename(Long candidateId, Long userId, String name) {
		Candidate candidate = findMemberCandidate(candidateId, userId);
		candidate.rename(name.trim());
		eventPublisher.publishEvent(new WorkspaceEvent(candidate.getWorkspace().getId(), CANDIDATE_UPDATED,
			new CandidateRenamed(candidateId, candidate.getName())));
		return CandidateResponse.of(candidate, summarizeVotes(List.of(candidateId), userId)
			.getOrDefault(candidateId, VoteSummary.EMPTY));
	}

	/** CAND-03 추가한 멤버나 호스트만 제외할 수 있다(제안). 연관 투표도 함께 지운다. */
	@Transactional
	public void remove(Long candidateId, Long userId) {
		Candidate candidate = candidateRepository.findWithAddedById(candidateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		Long workspaceId = candidate.getWorkspace().getId();
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		if (!member.isHost() && !candidate.isAddedBy(userId)) {
			throw new BusinessException(ErrorCode.NOT_CANDIDATE_OWNER);
		}
		eventPublisher.publishEvent(new CandidateRemovingEvent(candidateId));
		candidateRepository.delete(candidate);
		eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, CANDIDATE_REMOVED, new CandidateRemoved(candidateId)));
	}

	/** 비교표(CMP-01) 등에서 쓰는 Workspace의 후보지 엔티티. 접근 확인은 호출한 쪽에서 한다. */
	@Transactional(readOnly = true)
	public List<Candidate> findAllInWorkspace(Long workspaceId) {
		return candidateRepository.findAllByWorkspaceIdOrderByIdAsc(workspaceId);
	}

	/** 투표(VOTE-01~03)에서 쓰는 후보지. 없으면 404, 멤버가 아니면 403이다. */
	@Transactional(readOnly = true)
	public Candidate findMemberCandidate(Long candidateId, Long userId) {
		Candidate candidate = candidateRepository.findWithAddedById(candidateId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		workspaceAccess.requireMember(candidate.getWorkspace().getId(), userId);
		return candidate;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Long> findCandidateIds(Long workspaceId, Collection<Long> recommendationIds) {
		if (recommendationIds.isEmpty()) {
			return Map.of();
		}
		return candidateRepository.findAllByWorkspaceIdAndRecommendationIdIn(workspaceId, recommendationIds).stream()
			.collect(Collectors.toMap(Candidate::getRecommendationId, Candidate::getId, (first, second) -> first));
	}

	/** WS-04 투표가 먼저 지워진 뒤 후보지를 지운다. */
	@EventListener
	@Order(DELETE_ORDER)
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		candidateRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	/** AUTH-06 탈퇴해도 후보지는 남기고 추가한 멤버만 비운다. */
	@EventListener
	@Transactional
	public void onUserWithdrawing(UserWithdrawingEvent event) {
		candidateRepository.clearAddedBy(event.userId());
	}

	public Map<Long, VoteSummary> summarizeVotes(Collection<Long> candidateIds, Long viewerUserId) {
		VoteSummaryProvider provider = voteSummaryProvider.getIfAvailable();
		return provider == null || candidateIds.isEmpty() ? Map.of() : provider.summarize(candidateIds, viewerUserId);
	}

	/** LOC-01·02 경사도의 목적지는 최근 분석에서 추출한 학교·직장이고, 없으면 AI 서버가 가장 가까운 지하철역을 쓴다. */
	private LocationInfo analyzeLocation(Long workspaceId, double latitude, double longitude) {
		List<String> destinations = recommendationService.getLatestConditions(workspaceId)
			.map(ExtractedConditions::destinations)
			.orElse(List.of());
		SlopeResponse slope = callSafely("경사도", () ->
			aiClient.estimateSlope(new SlopeRequest(latitude, longitude, destinations.stream().limit(3).toList())));
		VehicleAccessResponse vehicle = callSafely("차량 진입성", () ->
			aiClient.estimateVehicleAccess(new VehicleAccessRequest(latitude, longitude)));
		return LocationInfo.of(slope, vehicle);
	}

	private static <T> T callSafely(String item, Supplier<T> call) {
		try {
			return call.get();
		} catch (RuntimeException e) {
			log.warn("{} 산출 실패. 정보 없음으로 둔다.", item, e);
			return null;
		}
	}

	public record CandidateRenamed(Long candidateId, String name) {
	}

	public record CandidateRemoved(Long candidateId) {
	}
}
