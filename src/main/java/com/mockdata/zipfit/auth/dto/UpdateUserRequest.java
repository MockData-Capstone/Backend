package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// TODO(AUTH-05): 프로필 이미지 업로드는 파일 저장소(S3) 도입 후 추가한다.
public record UpdateUserRequest(
	@NotBlank @Size(min = User.NICKNAME_MIN_LENGTH, max = User.NICKNAME_MAX_LENGTH) String nickname
) {
}
