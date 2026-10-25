package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.SocialLoginResponse;
import com.mockdata.zipfit.auth.dto.SocialSignupRequest;
import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.oauth.OAuthClient;
import com.mockdata.zipfit.auth.oauth.OAuthUserInfo;
import com.mockdata.zipfit.auth.repository.SocialAccountRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.security.JwtProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-02 소셜(카카오·구글) 로그인·가입 */
@Service
@RequiredArgsConstructor
public class AuthService {

	private final List<OAuthClient> oauthClients;
	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TokenService tokenService;
	private final JwtProvider jwtProvider;

	public String authorizeUrl(OAuthProvider provider) {
		return client(provider).authorizeUrl();
	}

	/**
	 * 인가 코드로 제공자 회원번호를 확인한다. 가입된 회원이면 토큰을 발급하고,
	 * 처음 온 회원이면 개인정보 동의·닉네임 확인을 위해 가입 토큰을 돌려준다.
	 * 외부 API 호출이 길어질 수 있어 트랜잭션은 토큰 발급에만 건다.
	 */
	public SocialLoginResponse socialLogin(OAuthProvider provider, String code) {
		OAuthUserInfo info = client(provider).fetchUserInfo(code);

		Optional<SocialAccount> account = socialAccountRepository.findByProviderAndProviderUserId(provider, info.providerUserId());
		if (account.isPresent()) {
			return SocialLoginResponse.login(tokenService.issue(account.get().getUser()));
		}

		// TODO(AUTH-08): 같은 이메일의 자체 가입 회원이 있으면 연동으로 안내한다.
		String nickname = suggestedNickname(info.nickname());
		return SocialLoginResponse.signupRequired(new SocialLoginResponse.SignupRequired(
			jwtProvider.createSignupToken(info),
			nickname,
			nickname != null && isNicknameAvailable(nickname),
			info.email(),
			info.profileImageUrl()
		));
	}

	@Transactional
	public TokenResponse socialSignup(SocialSignupRequest request) {
		if (!request.privacyAgreed()) {
			throw new BusinessException(ErrorCode.PRIVACY_AGREEMENT_REQUIRED);
		}
		OAuthUserInfo info = jwtProvider.parseSignupToken(request.signupToken())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SIGNUP_TOKEN));
		if (socialAccountRepository.existsByProviderAndProviderUserId(info.provider(), info.providerUserId())) {
			throw new BusinessException(ErrorCode.ALREADY_REGISTERED);
		}
		String nickname = request.nickname().trim();
		if (!isNicknameAvailable(nickname)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}

		User user = userRepository.save(new User(nickname, info.email(), info.profileImageUrl(), Instant.now()));
		socialAccountRepository.saveAndFlush(new SocialAccount(user, info.provider(), info.providerUserId()));
		return tokenService.issue(user);
	}

	private String suggestedNickname(String providerNickname) {
		if (providerNickname == null || providerNickname.isBlank()) {
			return null;
		}
		String trimmed = providerNickname.trim();
		return trimmed.length() > User.NICKNAME_MAX_LENGTH ? trimmed.substring(0, User.NICKNAME_MAX_LENGTH) : trimmed;
	}

	private boolean isNicknameAvailable(String nickname) {
		return User.isValidNickname(nickname) && !userRepository.existsByNickname(nickname);
	}

	private OAuthClient client(OAuthProvider provider) {
		return oauthClients.stream()
			.filter(client -> client.provider() == provider)
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
	}
}
