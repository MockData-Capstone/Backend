package com.mockdata.zipfit.auth.repository;

import com.mockdata.zipfit.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByNickname(String nickname);
}
