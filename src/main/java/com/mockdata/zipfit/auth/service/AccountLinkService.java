package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.oauth.OAuthClients;
import com.mockdata.zipfit.auth.oauth.OAuthUserInfo;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AUTH-08 소셜 계정 연동. 자체 가입 회원에 소셜 계정을 연동해 두 방식 모두로 로그인할 수 있게 한다.
 * 어느 경로든 기존 계정의 비밀번호를 확인한 뒤에만 연동한다.
 */
@Service
@RequiredArgsConstructor
public class AccountLinkService {

	private final OAuthClients oauthClients;
	private final JwtProvider jwtProvider;
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 소셜 로그인 중 같은 이메일의 자체 가입 회원이 있을 때(409 ACCOUNT_LINK_REQUIRED): 비밀번호 확인 후 연동하고 로그인한다.
	 * 비밀번호가 틀리면 연동도 로그인도 하지 않는다.
	 */
	@Transactional
	public TokenResponse linkWithToken(OAuthProvider provider, String linkToken, String password) {
		OAuthUserInfo info = jwtProvider.parseLinkToken(linkToken)
			.filter(parsed -> parsed.provider() == provider)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LINK_TOKEN));
		User user = userRepository.findByEmailAndPasswordIsNotNull(info.email())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LINK_TOKEN));
		verifyPassword(user, password);
		link(user, info);
		return tokenService.issue(user);
	}

	/** 내 정보 화면에서 연동: 로그인한 자체 가입 회원이 소셜 인가 코드와 비밀번호를 보낸다. */
	public void linkFromMyPage(Long userId, OAuthProvider provider, String code, String password) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!user.hasPassword()) {
			throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
		}
		verifyPassword(user, password);
		// 인가 코드는 일회용이라, 막힐 요청이면 제공자 API를 부르기 전에 거른다.
		if (socialAccountRepository.existsByUserIdAndProvider(userId, provider)) {
			throw new BusinessException(ErrorCode.PROVIDER_ALREADY_LINKED);
		}

		// 외부 API 호출은 트랜잭션 밖에서 한다.
		OAuthUserInfo info = oauthClients.get(provider).fetchUserInfo(code);
		transactionTemplate.executeWithoutResult(status -> link(user, info));
	}

	private void verifyPassword(User user, String password) {
		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_PASSWORD);
		}
	}

	private void link(User user, OAuthUserInfo info) {
		if (socialAccountRepository.existsByProviderAndProviderUserId(info.provider(), info.providerUserId())) {
			throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
		}
		if (socialAccountRepository.existsByUserIdAndProvider(user.getId(), info.provider())) {
			throw new BusinessException(ErrorCode.PROVIDER_ALREADY_LINKED);
		}
		socialAccountRepository.saveAndFlush(new SocialAccount(user, info.provider(), info.providerUserId()));
	}
}
