package com.mockdata.zipfit.tour.service;

import com.mockdata.zipfit.tour.entity.TourEnums.EndReason;

/**
 * 시그널링 방(/ws/signaling?tourId=)에 전할 투어 변경. 트랜잭션이 커밋된 뒤에 전달된다.
 * <ul>
 *   <li>TOUR_ENDED: 모든 참여자에게 알리고 연결을 닫는다 (SIG-05). data: {@link TourEnded}</li>
 *   <li>PARTICIPANT_REMOVED: 그 참여자의 연결을 닫고 나머지에게 PEER_LEFT를 보낸다. data: 회원 ID(Long)</li>
 * </ul>
 */
public record TourEvent(Long tourId, String type, Object data) {

	public static final String TOUR_ENDED = "TOUR_ENDED";
	public static final String PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED";

	public record TourEnded(Long tourId, EndReason reason) {
	}
}
