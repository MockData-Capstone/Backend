package com.mockdata.zipfit.tour.repository;

import com.mockdata.zipfit.tour.entity.TourParticipant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TourParticipantRepository extends JpaRepository<TourParticipant, Long> {

	List<TourParticipant> findAllByTourIdOrderByIdAsc(Long tourId);

	Optional<TourParticipant> findByTourIdAndUserId(Long tourId, Long userId);

	long countByTourIdAndLeftAtIsNull(Long tourId);

	@Modifying(flushAutomatically = true)
	@Query("delete from TourParticipant p where p.tour.id in (select t.id from TourSession t where t.workspace.id = :workspaceId)")
	int deleteAllByWorkspaceId(Long workspaceId);
}
