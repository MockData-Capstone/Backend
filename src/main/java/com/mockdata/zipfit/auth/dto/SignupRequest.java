package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AUTH-01 자체 회원가입 */
public record SignupRequest(
	@NotBlank @Email @Size(max = 255) String email,
	@ValidPassword String password,
	@NotBlank @Size(min = User.NICKNAME_MIN_LENGTH, max = User.NICKNAME_MAX_LENGTH) String nickname,
	boolean privacyAgreed
) {
}
