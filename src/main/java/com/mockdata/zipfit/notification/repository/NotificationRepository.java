package com.mockdata.zipfit.notification.repository;

import com.mockdata.zipfit.notification.entity.Notification;
import com.mockdata.zipfit.notification.entity.NotificationType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** 알림함 첫 페이지: 지금 멤버인 Workspace의, since 이후 갱신된 알림을 최신순으로. */
	@Query("""
		select n from Notification n
		where n.userId = :userId and n.workspaceId in :workspaceIds and n.updatedAt >= :since
		  and (:unreadOnly = false or n.read = false)
		order by n.updatedAt desc, n.id desc
		""")
	List<Notification> findFirstPage(
		Long userId,
		Collection<Long> workspaceIds,
		Instant since,
		boolean unreadOnly,
		Pageable pageable
	);

	/**
	 * 알림함 다음 페이지: 커서 (updatedAt, id)보다 오래된 알림.
	 * 첫 페이지와 나눈 이유: PostgreSQL은 ":cursorAt is null" 같은 null 파라미터의 타입을 정하지 못해 쿼리가 실패한다.
	 */
	@Query("""
		select n from Notification n
		where n.userId = :userId and n.workspaceId in :workspaceIds and n.updatedAt >= :since
		  and (:unreadOnly = false or n.read = false)
		  and (n.updatedAt < :cursorAt or (n.updatedAt = :cursorAt and n.id < :cursorId))
		order by n.updatedAt desc, n.id desc
		""")
	List<Notification> findPageAfter(
		Long userId,
		Collection<Long> workspaceIds,
		Instant since,
		boolean unreadOnly,
		Instant cursorAt,
		Long cursorId,
		Pageable pageable
	);

	@Query("""
		select count(n) from Notification n
		where n.userId = :userId and n.workspaceId in :workspaceIds and n.updatedAt >= :since and n.read = false
		""")
	long countUnread(Long userId, Collection<Long> workspaceIds, Instant since);

	List<Notification> findAllByUserIdAndReadFalse(Long userId);

	Optional<Notification> findFirstByUserIdAndWorkspaceIdAndTypeAndReadFalse(
		Long userId, Long workspaceId, NotificationType type
	);

	@Modifying(flushAutomatically = true)
	@Query("delete from Notification n where n.userId = :userId")
	int deleteAllByUserId(Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Notification n where n.workspaceId = :workspaceId")
	int deleteAllByWorkspaceId(Long workspaceId);
}
