package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.EventRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {
  Optional<EventRegistration> findByEventIdAndUserId(UUID eventId, UUID userId);

  List<EventRegistration> findByEventIdOrderByCreatedAtAsc(UUID eventId);

  List<EventRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<EventRegistration> findByIdAndEventId(UUID id, UUID eventId);

  long countByUserIdAndStatusIn(UUID userId, Collection<EventRegistration.Status> statuses);

  long countByEventIdAndStatusIn(UUID eventId, Collection<EventRegistration.Status> statuses);

  /** Waitlist order uses updatedAt so someone who cancels and re-registers joins the back of the queue. */
  Optional<EventRegistration> findFirstByEventIdAndStatusOrderByUpdatedAtAsc(
      UUID eventId, EventRegistration.Status status);

  @Query("""
      select r.event.id as eventId, count(r) as total from EventRegistration r
      where r.event.id in :eventIds and r.status in :statuses
      group by r.event.id
      """)
  List<EventCount> countByEventIds(
      @Param("eventIds") Collection<UUID> eventIds,
      @Param("statuses") Collection<EventRegistration.Status> statuses);

  interface EventCount {
    UUID getEventId();
    long getTotal();
  }
}
