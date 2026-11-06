package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.oauth.OAuthClients;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.event.UserWithdrawingEvent;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.storage.FileStorage;
import com.mockdata.zipfit.common.storage.ImageFormat;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** AUTH-04 내 정보 조회, AUTH-05 내 정보 수정, AUTH-06 회원 탈퇴 */
@Service
@RequiredArgsConstructor
public class UserService {

	// 프로필 이미지 용량 제한 (제안)
	public static final long PROFILE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
	private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

	private final OAuthClients oauthClients;
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TokenService tokenService;
	private final PasswordService passwordService;
	private final FileStorage fileStorage;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		return UserResponse.of(findUser(userId), providersOf(userId));
	}

	/** 닉네임 중복 확인. 형식(길이)에 맞지 않는 닉네임도 쓸 수 없는 것으로 본다. */
	@Transactional(readOnly = true)
	public boolean isNicknameAvailable(String nickname) {
		String trimmed = nickname.trim();
		return User.isValidNickname(trimmed) && !userRepository.existsByNickname(trimmed);
	}

	/**
	 * 전달된 필드만 바꾼다. 닉네임 변경은 회원을 참조하는 모든 Workspace의 멤버 목록·채팅에 그대로 반영된다.
	 * 비밀번호는 지금 비밀번호를 확인한 뒤 바꾸며, 카카오 전용 회원은 바꿀 수 없다.
	 */
	@Transactional
	public UserResponse updateMe(Long userId, UpdateUserRequest request) {
		User user = findUser(userId);
		if (request.nickname() != null) {
			changeNickname(user, request.nickname().trim());
		}
		if (request.newPassword() != null) {
			passwordService.changePassword(userId, request.currentPassword(), request.newPassword());
		}
		return UserResponse.of(user, providersOf(userId));
	}

	private void changeNickname(User user, String nickname) {
		if (nickname.equals(user.getNickname())) {
			return;
		}
		if (!User.isValidNickname(nickname)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}
		user.changeNickname(nickname);
	}

	/**
	 * 프로필 이미지 변경. 빈 값을 보내면 기본 이미지로 되돌린다.
	 * 허용 형식(jpg·png·webp)과 용량(5MB)을 넘으면 저장하지 않는다.
	 */
	@Transactional
	public UserResponse updateProfileImage(Long userId, MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return resetProfileImage(userId);
		}
		if (image.getSize() > PROFILE_IMAGE_MAX_BYTES) {
			throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
		}
		byte[] content = readBytes(image);
		ImageFormat format = ImageFormat.detect(content)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_IMAGE));

		User user = findUser(userId);
		String previousUrl = user.getProfileImageUrl();
		user.changeProfileImage(fileStorage.store(PROFILE_IMAGE_DIRECTORY, content, format.getExtension()));
		fileStorage.delete(previousUrl);
		return UserResponse.of(user, providersOf(userId));
	}

	private UserResponse resetProfileImage(Long userId) {
		User user = findUser(userId);
		fileStorage.delete(user.getProfileImageUrl());
		user.resetProfileImage();
		return UserResponse.of(user, providersOf(userId));
	}

	/**
	 * 회원 탈퇴. Workspace·채팅 등 다른 도메인은 {@link UserWithdrawingEvent}를 받아 먼저 정리한다.
	 * 다른 멤버가 있는 Workspace의 호스트면 그때 막히므로, 카카오 연결 끊기 같은 외부 호출보다 먼저 발행한다.
	 * 그다음 제공자 연결을 끊고(카카오) 인증 토큰·비밀번호 재설정 토큰·소셜 연동·프로필 이미지·회원을 삭제한다.
	 */
	@Transactional
	public void withdraw(Long userId) {
		User user = findUser(userId);
		eventPublisher.publishEvent(new UserWithdrawingEvent(userId));
		List<SocialAccount> accounts = socialAccountRepository.findAllByUserId(userId);
		for (SocialAccount account : accounts) {
			oauthClients.find(account.getProvider()).ifPresent(client -> client.unlink(account.getProviderUserId()));
		}
		tokenService.revokeAll(userId);
		passwordService.deleteResetTokens(userId);
		socialAccountRepository.deleteAll(accounts);
		fileStorage.delete(user.getProfileImageUrl());
		userRepository.delete(user);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	private List<OAuthProvider> providersOf(Long userId) {
		return socialAccountRepository.findAllByUserId(userId).stream()
			.map(SocialAccount::getProvider)
			.sorted()
			.toList();
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE);
		}
	}
}
