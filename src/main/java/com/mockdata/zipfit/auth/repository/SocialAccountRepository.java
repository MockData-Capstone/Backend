package com.mockdata.zipfit.auth.repository;

import com.mockdata.zipfit.auth.entity.OAuthProvider;
import com.mockdata.zipfit.auth.entity.SocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<SocialAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

	List<SocialAccount> findAllByUserId(Long userId);
}
