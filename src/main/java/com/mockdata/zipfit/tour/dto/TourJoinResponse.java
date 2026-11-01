package com.mockdata.zipfit.tour.dto;

/**
 * 투어 시작·참여·재참여 응답 (TOUR-01·02·05). 시그널링 접속 정보와 ICE 서버를 함께 준다.
 *
 * @param signalingPath 접속 주소. 뒤에 &token={accessToken}을 붙여 연결한다
 */
public record TourJoinResponse(TourResponse tour, String signalingPath, IceServersResponse ice) {
}
