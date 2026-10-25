package com.mockdata.zipfit.auth.repository;

import com.mockdata.zipfit.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 삭제된 행 수를 돌려준다. 같은 토큰으로 동시에 재발급하면 한 요청만 1을 받는다. */
	@Modifying
	@Query("delete from RefreshToken r where r.id = :id")
	int deleteByIdReturningCount(Long id);

	@Modifying
	@Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
	int deleteByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from RefreshToken r where r.user.id = :userId")
	int deleteAllByUserId(Long userId);

	@Modifying
	@Query("delete from RefreshToken r where r.user.id = :userId and r.expiresAt <= :now")
	int deleteExpiredByUserId(Long userId, Instant now);
}
