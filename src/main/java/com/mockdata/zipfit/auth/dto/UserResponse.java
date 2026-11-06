package com.mockdata.zipfit.auth.dto;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.User;
import java.util.ArrayList;
import java.util.List;

/**
 * AUTH-04 내 정보
 *
 * @param profileImageUrl null이면 기본 이미지
 * @param providers       연동된 로그인 방식: LOCAL(자체), KAKAO, GOOGLE
 * @param hasPassword     false면 비밀번호 변경 입력란을 숨긴다 (카카오 전용 회원)
 */
public record UserResponse(
	Long id,
	String email,
	String nickname,
	String profileImageUrl,
	List<String> providers,
	boolean hasPassword
) {

	public static final String LOCAL = "LOCAL";

	public static UserResponse of(User user, List<OAuthProvider> socialProviders) {
		List<String> providers = new ArrayList<>();
		if (user.hasPassword()) {
			providers.add(LOCAL);
		}
		socialProviders.forEach(provider -> providers.add(provider.name()));
		return new UserResponse(
			user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(), providers, user.hasPassword()
		);
	}
}
