package com.mockdata.zipfit.tour.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.tour.dto.CurrentTourResponse;
import com.mockdata.zipfit.tour.dto.IceServersResponse;
import com.mockdata.zipfit.tour.dto.TourHistoryResponse;
import com.mockdata.zipfit.tour.dto.TourJoinResponse;
import com.mockdata.zipfit.tour.service.TourService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 영상 연결은 시작·참여 응답의 signalingPath로 시그널링 소켓에 접속해 맺는다. */
@RestController
@RequiredArgsConstructor
public class TourController {

	private final TourService tourService;

	/** TOUR-01 실시간 투어 시작 (가장 최근 미니게임의 방문자) */
	@PostMapping("/api/workspaces/{workspaceId}/tours")
	@ResponseStatus(HttpStatus.CREATED)
	public TourJoinResponse start(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return tourService.start(workspaceId, userId);
	}

	/** TOUR-03 투어 상태·참여자 (진행 중인 투어가 없으면 tour: null) */
	@GetMapping("/api/workspaces/{workspaceId}/tours/current")
	public CurrentTourResponse getCurrent(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new CurrentTourResponse(tourService.getCurrent(workspaceId, userId).orElse(null));
	}

	/** TOUR-06 투어 이력 */
	@GetMapping("/api/workspaces/{workspaceId}/tours")
	public ListResponse<TourHistoryResponse> getHistory(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(tourService.getHistory(workspaceId, userId));
	}

	/** TOUR-02 실시간 투어 참여 (이미 참여 중이면 같은 정보를 다시 준다) */
	@PostMapping("/api/tours/{tourId}/participants")
	@ResponseStatus(HttpStatus.CREATED)
	public TourJoinResponse join(@CurrentUser Long userId, @PathVariable Long tourId) {
		return tourService.join(tourId, userId);
	}

	/** TOUR-04 투어 나가기 (시청자) */
	@DeleteMapping("/api/tours/{tourId}/participants/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void leave(@CurrentUser Long userId, @PathVariable Long tourId) {
		tourService.leave(tourId, userId);
	}

	/** TOUR-04 투어 종료 (방문자) */
	@DeleteMapping("/api/tours/{tourId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void end(@CurrentUser Long userId, @PathVariable Long tourId) {
		tourService.end(tourId, userId);
	}

	/** TOUR-05 방문자 투어 재참여 */
	@PostMapping("/api/tours/{tourId}/rejoin")
	public TourJoinResponse rejoin(@CurrentUser Long userId, @PathVariable Long tourId) {
		return tourService.rejoin(tourId, userId);
	}

	/** TOUR-02 보조 STUN·TURN 서버와 시간 제한 자격증명 */
	@GetMapping("/api/tours/{tourId}/ice-servers")
	public IceServersResponse getIceServers(@CurrentUser Long userId, @PathVariable Long tourId) {
		return tourService.issueIceServers(tourId, userId);
	}
}
