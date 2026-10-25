package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.dto.LoginRequest;
import com.mockdata.zipfit.auth.dto.SignupRequest;
import com.mockdata.zipfit.auth.dto.TokenResponse;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-01 자체 회원가입, AUTH-03 자체 로그인 */
@Service
public class LocalAuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	// 없는 이메일로 로그인해도 비밀번호 비교 시간을 똑같이 써서, 응답 시간으로 가입 여부가 드러나지 않게 한다.
	private final String dummyPasswordHash;

	public LocalAuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.dummyPasswordHash = passwordEncoder.encode("zipfit-dummy-password");
	}

	/** 가입 후 바로 로그인 상태가 되도록 토큰을 발급한다. */
	@Transactional
	public TokenResponse signup(SignupRequest request) {
		if (!request.privacyAgreed()) {
			throw new BusinessException(ErrorCode.PRIVACY_AGREEMENT_REQUIRED);
		}
		String email = User.normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		String nickname = request.nickname().trim();
		if (!User.isValidNickname(nickname)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (userRepository.existsByNickname(nickname)) {
			throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
		}

		User user = userRepository.saveAndFlush(
			new User(nickname, email, passwordEncoder.encode(request.password()), null, Instant.now())
		);
		return tokenService.issue(user);
	}

	public TokenResponse login(LoginRequest request) {
		Optional<User> user = userRepository.findByEmailAndPasswordIsNotNull(User.normalizeEmail(request.email()));
		String hash = user.map(User::getPassword).orElse(dummyPasswordHash);
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !matches) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		return tokenService.issue(user.get());
	}

	@Transactional(readOnly = true)
	public boolean isEmailAvailable(String email) {
		return !userRepository.existsByEmail(User.normalizeEmail(email));
	}

	/** 형식(길이)에 맞지 않는 닉네임도 쓸 수 없는 것으로 본다. */
	public boolean isNicknameAvailable(String nickname) {
		String trimmed = nickname.trim();
		return User.isValidNickname(trimmed) && !userRepository.existsByNickname(trimmed);
	}
}
