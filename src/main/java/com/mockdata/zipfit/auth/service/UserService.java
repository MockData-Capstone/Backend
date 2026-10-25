package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.oauth.OAuthClients;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.storage.FileStorage;
import com.mockdata.zipfit.common.storage.ImageFormat;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		return UserResponse.of(findUser(userId), providersOf(userId));
	}

	/** 닉네임 변경은 회원을 참조하는 모든 Workspace의 멤버 목록·채팅에 그대로 반영된다. */
	@Transactional
	public UserResponse updateMe(Long userId, UpdateUserRequest request) {
		User user = findUser(userId);
		String nickname = request.nickname().trim();
		if (!nickname.equals(user.getNickname())) {
			if (!User.isValidNickname(nickname)) {
				throw new BusinessException(ErrorCode.INVALID_INPUT);
			}
			if (userRepository.existsByNickname(nickname)) {
				throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
			}
			user.changeNickname(nickname);
		}
		return UserResponse.of(user, providersOf(userId));
	}

	/** 프로필 이미지 변경. 허용 형식(jpg·png·webp)과 용량(5MB)을 넘으면 저장하지 않는다. */
	@Transactional
	public UserResponse updateProfileImage(Long userId, MultipartFile image) {
		if (image == null || image.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE);
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

	/** 프로필 이미지를 비워 기본 이미지로 되돌린다. */
	@Transactional
	public UserResponse resetProfileImage(Long userId) {
		User user = findUser(userId);
		fileStorage.delete(user.getProfileImageUrl());
		user.resetProfileImage();
		return UserResponse.of(user, providersOf(userId));
	}

	/**
	 * 회원 탈퇴. 제공자 연결을 끊고(카카오) 인증 토큰·비밀번호 재설정 토큰·소셜 연동·프로필 이미지·회원을 삭제한다.
	 * TODO(AUTH-06): Workspace·채팅·투표·알림 도메인이 생기면 아래를 함께 처리한다.
	 *  - 다른 멤버가 있는 Workspace의 호스트면 탈퇴를 막는다(위임 후 탈퇴).
	 *  - 혼자 남은 Workspace는 삭제하고, 나머지 Workspace에서는 탈퇴 처리한다.
	 *  - 작성한 채팅 메시지는 내용을 삭제하고, 투표는 집계에서 제외한다.
	 *  - 알림·푸시 구독을 삭제한다.
	 */
	@Transactional
	public void withdraw(Long userId) {
		User user = findUser(userId);
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
