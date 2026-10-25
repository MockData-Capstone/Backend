package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.User;
import java.util.ArrayList;
import java.util.List;

/**
 * @param profileImageUrl null이면 기본 이미지
 * @param providers       연동된 로그인 방식: LOCAL(자체), KAKAO, GOOGLE
 */
public record UserResponse(
	Long id,
	String nickname,
	String profileImageUrl,
	String email,
	List<String> providers
) {

	public static final String LOCAL = "LOCAL";

	public static UserResponse of(User user, List<OAuthProvider> socialProviders) {
		List<String> providers = new ArrayList<>();
		if (user.hasPassword()) {
			providers.add(LOCAL);
		}
		socialProviders.forEach(provider -> providers.add(provider.name()));
		return new UserResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getEmail(), providers);
	}
}
