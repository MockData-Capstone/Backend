package com.mockdata.zipfit.workspace.dto;

import com.mockdata.zipfit.auth.entity.User;

/** 응답에 넣는 회원 요약. profileImageUrl이 null이면 기본 이미지다. */
public record MemberProfile(Long userId, String nickname, String profileImageUrl) {

	public static MemberProfile of(User user) {
		return new MemberProfile(user.getId(), user.getNickname(), user.getProfileImageUrl());
	}
}
