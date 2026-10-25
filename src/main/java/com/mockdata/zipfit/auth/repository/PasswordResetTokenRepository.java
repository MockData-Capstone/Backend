package com.mockdata.zipfit.auth.repository;

import com.mockdata.zipfit.auth.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** 삭제된 행 수를 돌려준다. 같은 토큰을 동시에 쓰면 한 요청만 1을 받는다 (일회용). */
	@Modifying
	@Query("delete from PasswordResetToken t where t.id = :id")
	int deleteByIdReturningCount(Long id);

	@Modifying
	@Query("delete from PasswordResetToken t where t.user.id = :userId")
	int deleteAllByUserId(Long userId);
}
