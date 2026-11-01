package com.mockdata.zipfit.vote.service;

import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.candidate.dto.VoteSummary;
import com.mockdata.zipfit.candidate.entity.Candidate;
import com.mockdata.zipfit.candidate.service.CandidateService;
import com.mockdata.zipfit.candidate.service.VoteSummaryProvider;
import com.mockdata.zipfit.common.event.CandidateRemovingEvent;
import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.event.WorkspaceDeletingEvent;
import com.mockdata.zipfit.common.event.WorkspaceEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.vote.dto.CandidateVotesResponse;
import com.mockdata.zipfit.vote.entity.Vote;
import com.mockdata.zipfit.vote.entity.VoteChoice;
import com.mockdata.zipfit.vote.repository.VoteRepository;
import com.mockdata.zipfit.workspace.service.MemberService;
import com.mockdata.zipfit.workspace.service.WorkspaceAccess;
import com.mockdata.zipfit.workspace.service.WorkspaceEventTypes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VOTE-01~03 후보지 투표.
 * 나가거나 내보내진 멤버, 탈퇴한 회원의 표는 지워 집계에서 뺀다 (MEM-05·06, AUTH-06).
 */
@Service
@RequiredArgsConstructor
public class VoteService implements VoteSummaryProvider {

	public static final String VOTE_UPDATED = "VOTE_UPDATED";

	private final VoteRepository voteRepository;
	private final CandidateService candidateService;
	private final UserRepository userRepository;
	private final WorkspaceAccess workspaceAccess;
	private final ApplicationEventPublisher eventPublisher;

	/** VOTE-01 첫 투표. 이미 투표했으면 변경(VOTE-03)을 써야 한다. */
	@Transactional
	public CandidateVotesResponse vote(Long candidateId, Long userId, VoteChoice choice) {
		Candidate candidate = candidateService.findMemberCandidate(candidateId, userId);
		if (voteRepository.existsByCandidateIdAndUserId(candidateId, userId)) {
			throw new BusinessException(ErrorCode.ALREADY_VOTED);
		}
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		try {
			voteRepository.saveAndFlush(new Vote(candidate, user, choice));
		} catch (DataIntegrityViolationException e) {
			// 같은 멤버가 동시에 두 번 눌렀다.
			throw new BusinessException(ErrorCode.ALREADY_VOTED);
		}
		return publishAndRespond(candidate, userId);
	}

	/** VOTE-03 본인의 표만 바꿀 수 있다. */
	@Transactional
	public CandidateVotesResponse change(Long candidateId, Long userId, VoteChoice choice) {
		Candidate candidate = candidateService.findMemberCandidate(candidateId, userId);
		Vote vote = voteRepository.findByCandidateIdAndUserId(candidateId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.VOTE_NOT_FOUND));
		vote.change(choice);
		voteRepository.flush();
		return publishAndRespond(candidate, userId);
	}

	/** VOTE-02 후보지별 찬성·반대 수와 멤버별 선택. 투표가 없는 후보지는 0표다. */
	@Transactional(readOnly = true)
	public List<CandidateVotesResponse> getResults(Long workspaceId, Long userId) {
		workspaceAccess.requireMember(workspaceId, userId);
		List<Candidate> candidates = candidateService.findAllInWorkspace(workspaceId);
		Map<Long, List<Vote>> votes = votesByCandidate(candidates.stream().map(Candidate::getId).toList());
		return candidates.stream()
			.map(c -> toResponse(c, votes.getOrDefault(c.getId(), List.of()), userId))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, VoteSummary> summarize(Collection<Long> candidateIds, Long viewerUserId) {
		return votesByCandidate(candidateIds).entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> {
				CandidateVotesResponse result = toResponse(null, entry.getValue(), viewerUserId);
				return new VoteSummary(result.agreeCount(), result.disagreeCount(), result.split(),
					result.myChoice() == null ? null : result.myChoice().name());
			}));
	}

	/** CAND-03 후보지를 제외하면 연관 투표를 지운다. */
	@EventListener
	@Transactional
	public void onCandidateRemoving(CandidateRemovingEvent event) {
		voteRepository.deleteAllByCandidateId(event.candidateId());
	}

	/** MEM-05·06 나가거나 내보내진 멤버의 표를 집계에서 뺀다. */
	@EventListener
	@Transactional
	public void onMemberRemoved(WorkspaceEvent event) {
		if (WorkspaceEventTypes.MEMBER_LEFT.equals(event.type()) || WorkspaceEventTypes.MEMBER_KICKED.equals(event.type())) {
			Long userId = ((MemberService.MemberRemoved) event.data()).userId();
			voteRepository.deleteAllByWorkspaceIdAndUserId(event.workspaceId(), userId);
		}
	}

	@EventListener
	@Transactional
	public void onUserWithdrawing(UserWithdrawingEvent event) {
		voteRepository.deleteAllByUserId(event.userId());
	}

	/** WS-04 후보지보다 먼저 지운다. */
	@EventListener
	@Order(0)
	@Transactional
	public void onWorkspaceDeleting(WorkspaceDeletingEvent event) {
		voteRepository.deleteAllByWorkspaceId(event.workspaceId());
	}

	private CandidateVotesResponse publishAndRespond(Candidate candidate, Long userId) {
		List<Vote> votes = votesByCandidate(List.of(candidate.getId())).getOrDefault(candidate.getId(), List.of());
		// 통지는 받는 멤버마다 내 표가 다르므로 myChoice 없이 보낸다.
		eventPublisher.publishEvent(new WorkspaceEvent(candidate.getWorkspace().getId(), VOTE_UPDATED,
			toResponse(candidate, votes, null)));
		return toResponse(candidate, votes, userId);
	}

	private Map<Long, List<Vote>> votesByCandidate(Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Map.of();
		}
		return voteRepository.findAllByCandidateIdInOrderByIdAsc(candidateIds).stream()
			.collect(Collectors.groupingBy(v -> v.getCandidate().getId()));
	}

	private static CandidateVotesResponse toResponse(Candidate candidate, List<Vote> votes, Long viewerUserId) {
		long agree = votes.stream().filter(v -> v.getChoice() == VoteChoice.AGREE).count();
		long disagree = votes.size() - agree;
		VoteChoice myChoice = votes.stream()
			.filter(v -> Objects.equals(v.getUser().getId(), viewerUserId))
			.map(Vote::getChoice)
			.findFirst()
			.orElse(null);
		return new CandidateVotesResponse(
			candidate == null ? null : candidate.getId(),
			candidate == null ? null : candidate.getName(),
			agree,
			disagree,
			agree > 0 && disagree > 0,
			myChoice,
			votes.stream()
				.map(v -> new CandidateVotesResponse.Voter(
					v.getUser().getId(), v.getUser().getNickname(), v.getUser().getProfileImageUrl(), v.getChoice()))
				.toList()
		);
	}
}
