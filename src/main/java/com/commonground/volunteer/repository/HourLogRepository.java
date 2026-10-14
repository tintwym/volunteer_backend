package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.HourLog;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HourLogRepository extends JpaRepository<HourLog, UUID> {
  Optional<HourLog> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<HourLog> findByRegistrationId(UUID registrationId);

  List<HourLog> findByUserIdOrderByWorkDateDescCreatedAtDesc(UUID userId);

  List<HourLog> findByUserIdAndStatusOrderByWorkDateAscCreatedAtAsc(UUID userId, HourLog.Status status);

  long countByUserIdAndStatus(UUID userId, HourLog.Status status);

  List<HourLog> findByOrganizationIdAndStatusOrderByCreatedAtAsc(UUID organizationId, HourLog.Status status);

  List<HourLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  long countByOrganizationIdAndStatus(UUID organizationId, HourLog.Status status);

  @Query("select coalesce(sum(h.hours), 0) from HourLog h where h.user.id = :userId and h.status = 'APPROVED'")
  BigDecimal sumApprovedHoursForUser(@Param("userId") UUID userId);

  @Query("select coalesce(sum(h.hours), 0) from HourLog h where h.organization.id = :orgId and h.status = 'APPROVED'")
  BigDecimal sumApprovedHoursForOrganization(@Param("orgId") UUID organizationId);

  @Query("""
      select h.user.id as userId, sum(h.hours) as total from HourLog h
      where h.organization.id = :orgId and h.status = 'APPROVED'
      group by h.user.id
      """)
  List<UserHours> approvedHoursByUser(@Param("orgId") UUID organizationId);

  interface UserHours {
    UUID getUserId();
    BigDecimal getTotal();
  }
}
