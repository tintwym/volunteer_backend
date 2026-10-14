package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);

  @Modifying
  @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
  int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
