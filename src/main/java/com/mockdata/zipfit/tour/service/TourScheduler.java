package com.mockdata.zipfit.tour.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 연결이 끊긴 방문자가 대기 시간 안에 돌아오지 않은 투어를 끝낸다 (TOUR-05, SIG-05). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourScheduler {

	private final TourService tourService;

	@Scheduled(fixedDelayString = "${tour.check-interval:5s}")
	public void tick() {
		check(Instant.now());
	}

	public void check(Instant now) {
		for (Long tourId : tourService.findToursWaitingForVisitor()) {
			try {
				tourService.endIfVisitorGone(tourId, now);
			} catch (RuntimeException e) {
				log.warn("투어 확인 실패: tourId={}", tourId, e);
			}
		}
	}
}
