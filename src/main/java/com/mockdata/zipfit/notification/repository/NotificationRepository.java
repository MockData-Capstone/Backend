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

	/**
	 * 알림함 한 페이지: 지금 멤버인 Workspace의, since 이후 갱신된 알림을 최신순으로.
	 * 커서는 (updatedAt, id)이며 처음에는 cursorAt을 null로 넘긴다.
	 */
	@Query("""
		select n from Notification n
		where n.userId = :userId and n.workspaceId in :workspaceIds and n.updatedAt >= :since
		  and (:unreadOnly = false or n.read = false)
		  and (:cursorAt is null or n.updatedAt < :cursorAt or (n.updatedAt = :cursorAt and n.id < :cursorId))
		order by n.updatedAt desc, n.id desc
		""")
	List<Notification> findPage(
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
