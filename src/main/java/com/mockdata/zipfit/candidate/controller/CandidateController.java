package com.mockdata.zipfit.candidate.controller;

import com.mockdata.zipfit.candidate.dto.AddCandidateRequest;
import com.mockdata.zipfit.candidate.dto.CandidateResponse;
import com.mockdata.zipfit.candidate.dto.RenameCandidateRequest;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateService candidateService;

	/** CAND-01 추천 위치를 후보지로 추가 */
	@PostMapping("/api/workspaces/{workspaceId}/candidates")
	@ResponseStatus(HttpStatus.CREATED)
	public CandidateResponse add(
		@CurrentUser Long userId,
		@PathVariable Long workspaceId,
		@Valid @RequestBody AddCandidateRequest request
	) {
		return candidateService.add(workspaceId, userId, request.recommendationId());
	}

	/** CAND-02 후보지 목록 (입지 정보·투표 집계 요약 포함) */
	@GetMapping("/api/workspaces/{workspaceId}/candidates")
	public ListResponse<CandidateResponse> getCandidates(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(candidateService.getCandidates(workspaceId, userId));
	}

	/** CAND-02 후보지 상세, LOC-03 입지 정보 */
	@GetMapping("/api/candidates/{candidateId}")
	public CandidateResponse getCandidate(@CurrentUser Long userId, @PathVariable Long candidateId) {
		return candidateService.getCandidate(candidateId, userId);
	}

	/** CAND-04 후보지 이름 수정 */
	@PatchMapping("/api/candidates/{candidateId}")
	public CandidateResponse rename(
		@CurrentUser Long userId,
		@PathVariable Long candidateId,
		@Valid @RequestBody RenameCandidateRequest request
	) {
		return candidateService.rename(candidateId, userId, request.name());
	}

	/** CAND-03 후보지 제외 (추가한 멤버 또는 호스트). 연관 투표도 지운다. */
	@DeleteMapping("/api/candidates/{candidateId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@CurrentUser Long userId, @PathVariable Long candidateId) {
		candidateService.remove(candidateId, userId);
	}
}
