package com.mockdata.zipfit.candidate.service;

import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.entity.LocationInfo;
import com.mockdata.zipfit.candidate.repository.CandidateRepository;
import com.mockdata.zipfit.location.noise.NoiseEstimator;
import com.mockdata.zipfit.location.noise.NoiseResult;
import com.mockdata.zipfit.location.sunlight.SunlightEstimator;
import com.mockdata.zipfit.location.sunlight.SunlightResult;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * LOC-04 일조·소음이 비어 있는 후보지를 다시 추정해 채운다.
 * 일조·소음 기능 전에 추가된 후보지와, 추가할 때 외부 API가 실패한 후보지가 대상이다.
 * 서버가 뜰 때 한 번 백그라운드로 돌고, 그래도 실패한 곳은 다음 시작 때 다시 시도한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationBackfillService {

	/** 한 번에 다시 추정할 후보지 수. 후보지 하나에 외부 API를 10번 남짓 부른다. */
	static final int BATCH_SIZE = 100;

	private final CandidateRepository candidateRepository;
	private final SunlightEstimator sunlightEstimator;
	private final NoiseEstimator noiseEstimator;
	private final TransactionTemplate transactionTemplate;

	@Value("${location.backfill-on-startup:true}")
	private boolean onStartup;

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		if (onStartup) {
			backfill();
		}
	}

	/** @return 일조나 소음을 하나라도 새로 채운 후보지 수 */
	public int backfill() {
		List<Long> ids = candidateRepository.findIdsMissingSunlightOrNoise(Limit.of(BATCH_SIZE));
		if (ids.isEmpty()) {
			return 0;
		}
		int filled = 0;
		for (Long id : ids) {
			if (backfillOne(id)) {
				filled++;
			}
		}
		log.info("일조·소음 다시 추정: 대상 {}곳 중 {}곳 채움", ids.size(), filled);
		return filled;
	}

	private boolean backfillOne(Long candidateId) {
		Optional<Candidate> found = candidateRepository.findById(candidateId);
		if (found.isEmpty()) {
			return false;
		}
		Candidate candidate = found.get();
		LocationInfo location = candidate.getLocation();
		double latitude = candidate.getLatitude();
		double longitude = candidate.getLongitude();

		// 외부 API는 트랜잭션 밖에서 부르고, 이미 있는 항목은 다시 부르지 않는다.
		SunlightResult sunlight = location != null && location.getSunlightGrade() != null ? null
			: callSafely("일조", candidateId, () -> sunlightEstimator.estimate(latitude, longitude).orElse(null));
		NoiseResult noise = location != null && location.getNoiseGrade() != null ? null
			: callSafely("소음", candidateId, () -> noiseEstimator.estimate(latitude, longitude).orElse(null));
		if (sunlight == null && noise == null) {
			return false;
		}
		// 계산하는 동안 후보지가 제외됐으면 아무것도 하지 않는다.
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> candidateRepository.findById(candidateId)
			.map(c -> {
				c.fillMissingSunlightAndNoise(sunlight, noise);
				return true;
			})
			.orElse(false)));
	}

	private static <T> T callSafely(String item, Long candidateId, Supplier<T> call) {
		try {
			return call.get();
		} catch (RuntimeException e) {
			log.warn("후보지 {} {} 다시 추정 실패. 다음 시작 때 다시 시도한다.", candidateId, item, e);
			return null;
		}
	}
}
