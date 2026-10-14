package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.HourLog;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HourDtos {

  private HourDtos() {}

  public record HourLogRequest(
      @NotNull @PastOrPresent LocalDate workDate,
      @NotNull @DecimalMin("0.25") @DecimalMax("24") BigDecimal hours,
      @Size(max = 2000) String description,
      UUID eventId
  ) {}

  /** {@code hours} lets the reviewer correct the logged amount before approving. */
  public record ReviewRequest(
      @NotNull Boolean approve,
      @Size(max = 2000) String note,
      @DecimalMin("0.25") @DecimalMax("24") BigDecimal hours
  ) {}

  public record HourLogResponse(
      UUID id,
      UUID userId,
      String userName,
      UUID eventId,
      String eventName,
      LocalDate workDate,
      BigDecimal hours,
      String description,
      HourLog.Status status,
      HourLog.Source source,
      String reviewedByName,
      Instant reviewedAt,
      String reviewNote,
      Instant createdAt
  ) {}

  public record BadgeResponse(
      int hours,
      String title,
      String tier,
      boolean earned,
      LocalDate earnedDate,
      BigDecimal progress
  ) {}

  public record BadgeSummary(
      BigDecimal totalHours,
      BadgeResponse current,
      BadgeResponse next,
      List<BadgeResponse> badges
  ) {}
}
