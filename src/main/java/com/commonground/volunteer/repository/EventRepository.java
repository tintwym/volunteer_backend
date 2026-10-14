package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Event;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {
  Optional<Event> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Event e where e.id = :id and e.organization.id = :orgId")
  Optional<Event> findForUpdate(@Param("id") UUID id, @Param("orgId") UUID organizationId);

  List<Event> findByOrganizationIdOrderByEventDateAscStartTimeAsc(UUID organizationId);

  List<Event> findByOrganizationIdAndStatusInOrderByEventDateAscStartTimeAsc(
      UUID organizationId, Collection<Event.Status> statuses);

  long countByOrganizationIdAndStatus(UUID organizationId, Event.Status status);

  long countByOrganizationIdAndEventDateGreaterThanEqualAndStatusIn(
      UUID organizationId, LocalDate from, Collection<Event.Status> statuses);
}
