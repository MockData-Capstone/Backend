package com.mockdata.zipfit.notification.repository;

import com.mockdata.zipfit.notification.entity.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

	Optional<PushSubscription> findByEndpoint(String endpoint);

	List<PushSubscription> findAllByUserId(Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from PushSubscription s where s.userId = :userId")
	int deleteAllByUserId(Long userId);
}
