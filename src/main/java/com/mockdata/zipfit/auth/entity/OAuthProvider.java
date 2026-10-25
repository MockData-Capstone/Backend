package com.mockdata.zipfit.auth.entity;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {
	KAKAO("kakao"),
	GOOGLE("google");

	private final String path;

	public static Optional<OAuthProvider> fromPath(String path) {
		return Arrays.stream(values())
			.filter(provider -> provider.path.equalsIgnoreCase(path))
			.findFirst();
	}
}
