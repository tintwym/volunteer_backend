package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MemberDtos {

  private MemberDtos() {}

  public record MemberResponse(
      UUID id,
      String email,
      String fullName,
      String phone,
      Role role,
      boolean active,
      BigDecimal totalHours,
      Instant joinedAt
  ) {}

  public record MemberUpdateRequest(Role role, Boolean active) {}

  public record OrganizerSummary(
      BigDecimal totalApprovedHours,
      long activeVolunteers,
      long activeLeaders,
      long upcomingEvents,
      long completedEvents,
      long pendingHourApprovals,
      long openIncidents,
      long openTasks
  ) {}

  public record MySummary(
      BigDecimal totalHours,
      long pendingHourLogs,
      long upcomingRegistrations,
      long completedEvents,
      long openTasks,
      long unreadNotifications,
      HourDtos.BadgeResponse currentBadge,
      HourDtos.BadgeResponse nextBadge
  ) {}
}
