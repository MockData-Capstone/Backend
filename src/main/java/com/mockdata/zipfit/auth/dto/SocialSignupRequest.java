package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialSignupRequest(
	@NotBlank String signupToken,
	@NotBlank @Size(min = User.NICKNAME_MIN_LENGTH, max = User.NICKNAME_MAX_LENGTH) String nickname,
	boolean privacyAgreed
) {
}
