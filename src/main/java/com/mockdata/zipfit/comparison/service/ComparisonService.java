package com.mockdata.zipfit.comparison.service;

import com.mockdata.zipfit.candidate.dto.LocationResponse;
import com.mockdata.zipfit.candidate.dto.VoteSummary;
import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.comparison.dto.ComparisonResponse;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMP-01 비교표. 같은 데이터로 만들므로 모든 멤버에게 같은 내용이 보인다. */
@Service
@RequiredArgsConstructor
public class ComparisonService {

	private final CandidateService candidateService;
	private final WorkspaceAccess workspaceAccess;

	/**
	 * @param candidateIds 비교할 후보지. 비어 있으면 Workspace의 후보지 전체다.
	 */
	@Transactional(readOnly = true)
	public ComparisonResponse compare(Long workspaceId, Long userId, Set<Long> candidateIds) {
		workspaceAccess.requireMember(workspaceId, userId);
		List<Candidate> all = candidateService.findAllInWorkspace(workspaceId);
		List<Candidate> targets = candidateIds.isEmpty()
			? all
			: all.stream().filter(c -> candidateIds.contains(c.getId())).toList();
		if (targets.size() < candidateIds.size()) {
			throw new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND);
		}
		if (targets.size() < 2) {
			throw new BusinessException(ErrorCode.NOT_ENOUGH_CANDIDATES);
		}
		Map<Long, VoteSummary> votes = candidateService.summarizeVotes(targets.stream().map(Candidate::getId).toList(), userId);
		List<ComparisonResponse.Row> rows = targets.stream()
			.map(c -> {
				LocationResponse location = LocationResponse.of(c.getLocation());
				return new ComparisonResponse.Row(
					c.getId(), c.getName(), c.getLatitude(), c.getLongitude(), location.slope(), location.vehicleAccess(),
					votes.getOrDefault(c.getId(), VoteSummary.EMPTY)
				);
			})
			.toList();
		return new ComparisonResponse(Instant.now(), rows);
	}
}
