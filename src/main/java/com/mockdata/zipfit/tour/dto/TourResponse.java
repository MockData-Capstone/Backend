package com.mockdata.zipfit.tour.dto;

import com.mockdata.zipfit.tour.entity.TourEnums.ConnectionState;
import com.mockdata.zipfit.tour.entity.TourEnums.EndReason;
import com.mockdata.zipfit.tour.entity.TourEnums.TourRole;
import com.mockdata.zipfit.tour.entity.TourEnums.TourStatus;
import java.time.Instant;
import java.util.List;

/**
 * 투어 상태·참여자 (TOUR-03)
 *
 * @param visitorConnected false면 "방문자 연결 끊김"을 표시한다
 * @param participants     지금 참여 중인 사람 (방문자 포함)
 */
public record TourResponse(
	Long tourId,
	Long workspaceId,
	Long gameId,
	TourStatus status,
	Visitor visitor,
	boolean visitorConnected,
	List<Participant> participants,
	int participantCount,
	int maxParticipants,
	Instant startedAt,
	Instant endedAt,
	EndReason endReason
) {

	public record Visitor(Long userId, String nickname) {
	}

	public record Participant(Long userId, String nickname, TourRole role, ConnectionState connectionState, Instant joinedAt) {
	}
}
