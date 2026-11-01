package com.mockdata.zipfit.vote.controller;

import com.mockdata.zipfit.common.dto.ListResponse;
import com.mockdata.zipfit.common.security.CurrentUser;
import com.mockdata.zipfit.vote.dto.CandidateVotesResponse;
import com.mockdata.zipfit.vote.dto.VoteRequest;
import com.mockdata.zipfit.vote.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VoteController {

	private final VoteService voteService;

	/** VOTE-01 후보지 투표 (찬성/반대) */
	@PostMapping("/api/candidates/{candidateId}/votes")
	@ResponseStatus(HttpStatus.CREATED)
	public CandidateVotesResponse vote(
		@CurrentUser Long userId,
		@PathVariable Long candidateId,
		@Valid @RequestBody VoteRequest request
	) {
		return voteService.vote(candidateId, userId, request.choice());
	}

	/** VOTE-03 내 투표 변경 */
	@PatchMapping("/api/candidates/{candidateId}/votes/me")
	public CandidateVotesResponse change(
		@CurrentUser Long userId,
		@PathVariable Long candidateId,
		@Valid @RequestBody VoteRequest request
	) {
		return voteService.change(candidateId, userId, request.choice());
	}

	/** VOTE-02 투표 결과 조회 */
	@GetMapping("/api/workspaces/{workspaceId}/votes")
	public ListResponse<CandidateVotesResponse> getResults(@CurrentUser Long userId, @PathVariable Long workspaceId) {
		return new ListResponse<>(voteService.getResults(workspaceId, userId));
	}
}
