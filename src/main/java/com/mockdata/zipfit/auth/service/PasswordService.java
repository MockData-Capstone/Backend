package com.mockdata.zipfit.auth.service;

import com.mockdata.zipfit.auth.entity.PasswordResetToken;
import com.mockdata.zipfit.auth.entity.User;
import com.mockdata.zipfit.auth.repository.PasswordResetTokenRepository;
import com.mockdata.zipfit.auth.repository.UserRepository;
import com.mockdata.zipfit.common.exception.BusinessException;
import com.mockdata.zipfit.common.exception.ErrorCode;
import com.mockdata.zipfit.common.mail.MailService;
import com.mockdata.zipfit.common.security.SecureTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-05 비밀번호 변경, AUTH-07 비밀번호 재설정 */
@Service
public class PasswordService {

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;
	private final MailService mailService;
	private final String frontendUrl;
	private final Duration resetTokenExpiration;

	public PasswordService(
		UserRepository userRepository,
		PasswordResetTokenRepository passwordResetTokenRepository,
		PasswordEncoder passwordEncoder,
		TokenService tokenService,
		MailService mailService,
		@Value("${app.frontend-url}") String frontendUrl,
		@Value("${app.password-reset.expiration}") Duration resetTokenExpiration
	) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.mailService = mailService;
		this.frontendUrl = frontendUrl.replaceAll("/+$", "");
		this.resetTokenExpiration = resetTokenExpiration;
	}

	/** AUTH-05 비밀번호 변경. 비밀번호가 없는 소셜 전용 회원은 변경할 수 없다. */
	@Transactional
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!user.hasPassword()) {
			throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
		}
		if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_PASSWORD);
		}
		user.changePassword(passwordEncoder.encode(newPassword));
	}

	/**
	 * AUTH-07 재설정 메일 요청. 계정이 있는지 드러나지 않도록 결과와 상관없이 같은 응답을 준다.
	 * <ul>
	 *   <li>자체 가입 회원: 일회용 재설정 링크를 보낸다. 이전에 받은 링크는 무효가 된다.</li>
	 *   <li>소셜 전용 회원: 비밀번호가 없으므로 소셜 로그인을 안내하는 메일을 보낸다.</li>
	 *   <li>가입되지 않은 이메일: 아무것도 보내지 않는다.</li>
	 * </ul>
	 */
	@Transactional
	public void requestReset(String email) {
		String normalized = User.normalizeEmail(email);
		Optional<User> localUser = userRepository.findByEmailAndPasswordIsNotNull(normalized);
		if (localUser.isPresent()) {
			User user = localUser.get();
			passwordResetTokenRepository.deleteAllByUserId(user.getId());
			String token = SecureTokens.generate();
			passwordResetTokenRepository.save(
				new PasswordResetToken(user, SecureTokens.sha256(token), Instant.now().plus(resetTokenExpiration))
			);
			mailService.send(normalized, "[ZIPFIT] 비밀번호 재설정 안내", resetMailText(token));
		} else if (userRepository.existsByEmail(normalized)) {
			mailService.send(normalized, "[ZIPFIT] 비밀번호 재설정 안내", socialOnlyMailText());
		}
	}

	/** AUTH-07 재설정 확인. 비밀번호를 바꾸고 기존 인증 토큰을 모두 폐기해 다른 기기의 로그인을 끊는다. */
	@Transactional
	public void confirmReset(String token, String newPassword) {
		PasswordResetToken saved = passwordResetTokenRepository.findByTokenHash(SecureTokens.sha256(token))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
		if (saved.isExpired(Instant.now())) {
			throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
		}
		// 일회용: 같은 토큰을 동시에 써도 한 번만 성공한다.
		if (passwordResetTokenRepository.deleteByIdReturningCount(saved.getId()) == 0) {
			throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
		}
		User user = saved.getUser();
		user.changePassword(passwordEncoder.encode(newPassword));
		tokenService.revokeAll(user.getId());
	}

	@Transactional
	public void deleteResetTokens(Long userId) {
		passwordResetTokenRepository.deleteAllByUserId(userId);
	}

	private String resetMailText(String token) {
		long minutes = resetTokenExpiration.toMinutes();
		return """
			ZIPFIT 비밀번호 재설정을 요청하셨습니다.
			아래 링크에서 새 비밀번호를 설정해주세요. 링크는 %d분 동안 한 번만 사용할 수 있습니다.

			%s/password-reset?token=%s

			직접 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다.
			""".formatted(minutes, frontendUrl, token);
	}

	private String socialOnlyMailText() {
		return """
			ZIPFIT 비밀번호 재설정을 요청하셨습니다.
			이 이메일로 가입된 계정은 카카오(소셜) 로그인으로 가입한 계정이라 비밀번호가 없습니다.
			로그인 화면에서 카카오 로그인을 이용해주세요.

			직접 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다.
			""";
	}
}
