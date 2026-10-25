package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.User;
import java.util.List;

/**
 * @param profileImageUrl null이면 기본 이미지
 * @param providers       연동된 로그인 방식
 */
public record UserResponse(
	Long id,
	String nickname,
	String profileImageUrl,
	String email,
	List<OAuthProvider> providers
) {

	public static UserResponse of(User user, List<OAuthProvider> providers) {
		return new UserResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getEmail(), providers);
	}
}
