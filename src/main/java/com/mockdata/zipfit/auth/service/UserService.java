package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.UpdateUserRequest;
import com.mockdata.zipfit.auth.dto.UserResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.oauth.OAuthClient;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-04 내 정보 조회, AUTH-05 내 정보 수정, AUTH-06 회원 탈퇴 */
@Service
@RequiredArgsConstructor
public class UserService {

	private final List<OAuthClient> oauthClients;
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TokenService tokenService;

	@Transactional(readOnly = true)
	public UserResponse getMe(Long userId) {
		return UserResponse.of(findUser(userId), providersOf(userId));
	}

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

	/** 프로필 이미지를 비워 기본 이미지로 되돌린다. */
	@Transactional
	public UserResponse resetProfileImage(Long userId) {
		User user = findUser(userId);
		user.resetProfileImage();
		return UserResponse.of(user, providersOf(userId));
	}

	/**
	 * 회원 탈퇴. 제공자 연결을 끊고(카카오) 인증 토큰·소셜 연동·회원을 삭제한다.
	 * TODO(AUTH-06): Workspace·채팅·투표·알림 도메인이 생기면 아래를 함께 처리한다.
	 *  - 다른 멤버가 있는 Workspace의 호스트면 탈퇴를 막는다(위임 후 탈퇴).
	 *  - 혼자 남은 Workspace는 삭제하고, 나머지 Workspace에서는 탈퇴 처리한다.
	 *  - 작성한 채팅 메시지는 내용을 삭제하고, 투표는 집계에서 제외한다.
	 *  - 알림·푸시 구독·비밀번호 재설정 토큰을 삭제한다.
	 */
	@Transactional
	public void withdraw(Long userId) {
		User user = findUser(userId);
		List<SocialAccount> accounts = socialAccountRepository.findAllByUserId(userId);
		for (SocialAccount account : accounts) {
			oauthClients.stream()
				.filter(client -> client.provider() == account.getProvider())
				.findFirst()
				.ifPresent(client -> client.unlink(account.getProviderUserId()));
		}
		tokenService.revokeAll(userId);
		socialAccountRepository.deleteAll(accounts);
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
}
