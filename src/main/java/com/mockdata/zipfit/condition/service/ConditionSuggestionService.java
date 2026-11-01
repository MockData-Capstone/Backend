package com.mockdata.zipfit.condition.service;

import com.mockdata.zipfit.ai.dto.Confidence;
import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.ai.dto.SlopeGrade;
import com.mockdata.zipfit.ai.dto.VehicleAccess;
import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.entity.LocationInfo;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.condition.dto.ConditionResponse;
import com.mockdata.zipfit.condition.dto.SuggestionResponse;
import com.mockdata.zipfit.condition.entity.AcceptedSuggestion;
import com.mockdata.zipfit.condition.entity.SuggestionType;
import com.mockdata.zipfit.condition.repository.AcceptedSuggestionRepository;
import com.mockdata.zipfit.condition.repository.HouseConditionRepository;
import com.mockdata.zipfit.recommendation.service.RecommendationService;
import com.mockdata.zipfit.workspace.entity.WorkspaceMember;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REC-03 조건 제안. 대화에 없던 조건 중 후보 선택에 영향을 주는 것을 근거와 함께 제안한다.
 * <ul>
 *   <li>근거는 입지 분석이 끝난 후보지의 경사도·진입성이다. 근거가 없으면 제안하지 않는다.</li>
 *   <li>대화에서 이미 나온 조건은 다시 제안하지 않는다.</li>
 *   <li>요청할 때 최신 후보지로 만들므로, 입지 분석이 새로 끝난 후보지가 생기면 제안도 바뀐다.</li>
 *   <li>멤버가 받아들일 때만 조건 확정 초기값에 넣는다. 이미 확정했으면 확정 조건에도 반영한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ConditionSuggestionService {

	public static final String SUGGESTION_ACCEPTED = "CONDITION_SUGGESTION_ACCEPTED";

	private final RecommendationService recommendationService;
	private final CandidateService candidateService;
	private final AcceptedSuggestionRepository acceptedRepository;
	private final HouseConditionRepository conditionRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional(readOnly = true)
	public List<SuggestionResponse> getSuggestions(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		return suggest(workspaceId);
	}

	/** 제안을 받아들인다. 지금 근거가 없는 제안은 받아들일 수 없다. 이미 받아들였으면 그대로 둔다. */
	@Transactional
	public List<SuggestionResponse> accept(Long workspaceId, Long userId, SuggestionType type) {
		WorkspaceMember member = workspaceAccess.requireMember(workspaceId, userId);
		boolean available = suggest(workspaceId).stream().anyMatch(s -> s.type() == type);
		Long analysisId = recommendationService.getLatestCompletedAnalysisId(workspaceId)
			.filter(id -> available)
			.orElseThrow(() -> new BusinessException(ErrorCode.SUGGESTION_NOT_AVAILABLE));
		if (!acceptedRepository.existsByWorkspaceIdAndAnalysisIdAndType(workspaceId, analysisId, type)) {
			acceptedRepository.save(new AcceptedSuggestion(member.getWorkspace(), analysisId, type));
			conditionRepository.findByWorkspaceId(workspaceId).ifPresent(condition -> {
				condition.acceptSuggestion(type == SuggestionType.SLOPE, type == SuggestionType.VEHICLE_ACCESS, userId,
					member.getUser().getNickname(), Instant.now());
				eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, ConditionService.CONDITION_UPDATED,
					ConditionResponse.of(condition)));
			});
			eventPublisher.publishEvent(new WorkspaceEvent(workspaceId, SUGGESTION_ACCEPTED, new Accepted(type)));
		}
		return suggest(workspaceId);
	}

	/** 최근 분석에서 받아들인 제안. 조건 확정 초기값(COND-01)에 더한다. */
	@Transactional(readOnly = true)
	public Set<SuggestionType> acceptedTypes(Long workspaceId) {
		return recommendationService.getLatestCompletedAnalysisId(workspaceId)
			.map(analysisId -> acceptedRepository.findAllByWorkspaceIdAndAnalysisId(workspaceId, analysisId).stream()
				.map(AcceptedSuggestion::getType)
				.collect(Collectors.toSet()))
			.orElse(Set.of());
	}

	private List<SuggestionResponse> suggest(Long workspaceId) {
		Optional<ExtractedConditions> conditions = recommendationService.getLatestConditions(workspaceId);
		if (conditions.isEmpty()) {
			return List.of();
		}
		Set<SuggestionType> accepted = acceptedTypes(workspaceId);
		List<Candidate> candidates = candidateService.findAllInWorkspace(workspaceId);
		List<SuggestionResponse> suggestions = new ArrayList<>();

		if (conditions.get().avoidSlope() == null) {
			List<SuggestionResponse.Evidence> steep = candidates.stream()
				.filter(c -> c.getLocation() != null && c.getLocation().getSlopeGrade() == SlopeGrade.STEEP)
				.map(c -> new SuggestionResponse.Evidence(c.getId(), c.getName(), slopeDetail(c.getLocation())))
				.toList();
			if (!steep.isEmpty()) {
				suggestions.add(new SuggestionResponse(SuggestionType.SLOPE,
					"대화에 경사 조건은 없었지만, %s에는 가파른 구간이 있어요. 경사 조건(오르막 회피)을 추가할까요?"
						.formatted(names(steep)),
					steep, accepted.contains(SuggestionType.SLOPE)));
			}
		}
		if (conditions.get().needVehicleAccess() == null) {
			List<SuggestionResponse.Evidence> blocked = candidates.stream()
				.filter(c -> c.getLocation() != null && (c.getLocation().getVehicleAccess() == VehicleAccess.IMPOSSIBLE
					|| c.getLocation().getVehicleAccess() == VehicleAccess.LIMITED))
				.map(c -> new SuggestionResponse.Evidence(c.getId(), c.getName(), vehicleDetail(c.getLocation())))
				.toList();
			if (!blocked.isEmpty()) {
				suggestions.add(new SuggestionResponse(SuggestionType.VEHICLE_ACCESS,
					"대화에 차량 조건은 없었지만, %s은(는) 차량 진입이 어려워요. 차량 조건(차량 진입 필요)을 추가할까요?"
						.formatted(names(blocked)),
					blocked, accepted.contains(SuggestionType.VEHICLE_ACCESS)));
			}
		}
		return suggestions;
	}

	private static String names(List<SuggestionResponse.Evidence> evidence) {
		String first = "후보지 '" + evidence.getFirst().candidateName() + "'";
		return evidence.size() == 1 ? first : first + " 외 " + (evidence.size() - 1) + "곳";
	}

	private static String slopeDetail(LocationInfo l) {
		return String.format(Locale.ROOT, "%s까지 최대 경사 %.1f%% (가파름%s)",
			l.getSlopeDestination() == null ? "목적지" : l.getSlopeDestination(), l.getSlopeMaxPercent(), estimated(l.getSlopeConfidence()));
	}

	private static String vehicleDetail(LocationInfo l) {
		String verdict = l.getVehicleAccess() == VehicleAccess.IMPOSSIBLE ? "불가" : "제한적";
		return "차량 진입 " + verdict + estimated(l.getVehicleConfidence()) + (l.getVehicleReason() == null ? "" : ": " + l.getVehicleReason());
	}

	private static String estimated(Confidence confidence) {
		return confidence == Confidence.HIGH ? "" : ", 추정치";
	}

	public record Accepted(SuggestionType type) {
	}
}
