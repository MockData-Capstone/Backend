package com.mockdata.zipfit.recommendation.service;

import com.mockdata.zipfit.ai.AiClient;
import com.mockdata.zipfit.ai.dto.AiRecommendationRequest;
import com.mockdata.zipfit.ai.dto.AiRecommendationResponse;
import com.mockdata.zipfit.ai.dto.ExtractedConditions;
import com.mockdata.zipfit.chat.service.ChatService;
import com.mockdata.zipfit.chat.service.ChatService.ConversationLine;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.recommendation.entity.Analysis;
import com.mockdata.zipfit.recommendation.entity.AnalysisFailure;
import com.mockdata.zipfit.recommendation.entity.AnalysisStatus;
import com.mockdata.zipfit.recommendation.entity.Recommendation;
import com.mockdata.zipfit.recommendation.repository.AnalysisRepository;
import com.mockdata.zipfit.recommendation.repository.RecommendationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 접수된 분석을 요청 스레드와 따로 진행한다 (REC-01).
 * AI 서버 호출은 트랜잭션 밖에서 하고, 결과 저장과 통지는 짧은 트랜잭션으로 한다.
 */
@Slf4j
@Component
public class AnalysisWorker {

	private final AnalysisRepository analysisRepository;
	private final RecommendationRepository recommendationRepository;
	private final ChatService chatService;
	private final AiClient aiClient;
	private final ApplicationEventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;
	private final int maxMessages;
	private final Duration timeout;

	public AnalysisWorker(
		AnalysisRepository analysisRepository,
		RecommendationRepository recommendationRepository,
		ChatService chatService,
		AiClient aiClient,
		ApplicationEventPublisher eventPublisher,
		TransactionTemplate transactionTemplate,
		JsonMapper jsonMapper,
		@Value("${ai.max-messages:300}") int maxMessages,
		@Value("${ai.analysis-timeout:5m}") Duration timeout
	) {
		this.analysisRepository = analysisRepository;
		this.recommendationRepository = recommendationRepository;
		this.chatService = chatService;
		this.aiClient = aiClient;
		this.eventPublisher = eventPublisher;
		this.transactionTemplate = transactionTemplate;
		this.jsonMapper = jsonMapper;
		this.maxMessages = maxMessages;
		this.timeout = timeout;
	}

	@Async
	@TransactionalEventListener
	public void run(RecommendationService.AnalysisRequestedEvent event) {
		AiRecommendationResponse response;
		try {
			response = aiClient.recommend(new AiRecommendationRequest(event.workspaceId(),
				toMessages(chatService.getConversation(event.workspaceId(), maxMessages))));
		} catch (RuntimeException e) {
			log.warn("AI 분석 실패: analysisId={}", event.analysisId(), e);
			fail(event.analysisId(), AnalysisFailure.AI_ERROR, null);
			return;
		}
		ExtractedConditions conditions = response.conditions();
		if (conditions == null || conditions.isEmpty()) {
			fail(event.analysisId(), AnalysisFailure.NO_CONDITIONS, conditions);
			return;
		}
		complete(event.analysisId(), response);
	}

	/** 서버가 분석 중에 재시작되는 등으로 끝나지 않은 분석은 실패로 바꿔 다시 요청할 수 있게 한다. */
	@Scheduled(fixedDelayString = "${ai.analysis-timeout-check-interval:1m}")
	public void failTimedOut() {
		analysisRepository.findAllByStatusAndRequestedAtBefore(AnalysisStatus.IN_PROGRESS, Instant.now().minus(timeout))
			.forEach(analysis -> fail(analysis.getId(), AnalysisFailure.TIMEOUT, null));
	}

	private void complete(Long analysisId, AiRecommendationResponse response) {
		transactionTemplate.executeWithoutResult(status -> analysisRepository.findById(analysisId)
			.filter(Analysis::isInProgress)
			.ifPresent(analysis -> {
				List<Recommendation> saved = recommendationRepository.saveAll(response.places().stream()
					.map(place -> new Recommendation(analysis, truncate(place.placeName(), 100), place.latitude(),
						place.longitude(), truncate(place.reason(), Recommendation.REASON_MAX_LENGTH),
						place.kakaoPlaceId()))
					.toList());
				analysis.complete(jsonMapper.writeValueAsString(response.conditions()), Instant.now());
				eventPublisher.publishEvent(new WorkspaceEvent(analysis.getWorkspace().getId(),
					RecommendationService.RECOMMENDATION_COMPLETED, new AnalysisCompleted(analysisId, saved.size())));
			}));
	}

	private void fail(Long analysisId, AnalysisFailure failure, ExtractedConditions conditions) {
		transactionTemplate.executeWithoutResult(status -> analysisRepository.findById(analysisId)
			.filter(Analysis::isInProgress)
			.ifPresent(analysis -> {
				analysis.fail(failure, conditions == null ? null : jsonMapper.writeValueAsString(conditions), Instant.now());
				eventPublisher.publishEvent(new WorkspaceEvent(analysis.getWorkspace().getId(),
					RecommendationService.RECOMMENDATION_FAILED,
					new AnalysisFailed(analysisId, failure, failure.getMessage())));
			}));
	}

	/** 회원 ID 대신 대화에 처음 나온 순서대로 "멤버1", "멤버2"로 바꿔 보낸다. */
	private static List<AiRecommendationRequest.Message> toMessages(List<ConversationLine> lines) {
		Map<Long, String> speakers = new LinkedHashMap<>();
		return lines.stream()
			.map(line -> new AiRecommendationRequest.Message(
				speakers.computeIfAbsent(line.senderId(), id -> "멤버" + (speakers.size() + 1)),
				line.content(),
				line.sentAt()
			))
			.toList();
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	public record AnalysisCompleted(Long analysisId, int recommendationCount) {
	}

	public record AnalysisFailed(Long analysisId, AnalysisFailure reason, String message) {
	}
}
