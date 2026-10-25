package com.mockdata.zipfit.auth.repository;

import com.mockdata.zipfit.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByNickname(String nickname);

	boolean existsByEmail(String email);

	/** 자체 가입 회원만 조회한다. 같은 이메일의 소셜 회원은 비밀번호가 없어 제외된다. */
	Optional<User> findByEmailAndPasswordIsNotNull(String email);
}
