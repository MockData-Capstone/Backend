package com.mockdata.zipfit.candidate.dto;

import com.mockdata.zipfit.candidate.entity.Candidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CAND-04 */
public record RenameCandidateRequest(@NotBlank @Size(max = Candidate.NAME_MAX_LENGTH) String name) {
}
