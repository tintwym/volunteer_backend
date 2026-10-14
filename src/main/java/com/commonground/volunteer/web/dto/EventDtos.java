package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.EventRegistration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class EventDtos {

  private EventDtos() {}

  public record EventRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 5000) String description,
      @Size(max = 64) String category,
      @Size(max = 200) String venue,
      @Size(max = 500) String address,
      Boolean remote,
      @Size(max = 500) String meetingLink,
      @NotNull LocalDate date,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime,
      @NotNull @Min(1) @Max(10000) Integer capacity,
      Event.Status status,
      @Size(max = 500) String imageUrl
  ) {}

  public record EventResponse(
      UUID id,
      String name,
      String description,
      String category,
      String venue,
      String address,
      boolean remote,
      String meetingLink,
      LocalDate date,
      LocalTime startTime,
      LocalTime endTime,
      double durationHours,
      int capacity,
      long registeredCount,
      long waitlistCount,
      Event.Status status,
      String imageUrl,
      /** Only returned to organizers and leaders. */
      String checkInCode,
      EventRegistration.Status myRegistrationStatus,
      Instant createdAt
  ) {}

  public record RegistrationResponse(
      UUID id,
      UUID eventId,
      String eventName,
      LocalDate eventDate,
      LocalTime startTime,
      LocalTime endTime,
      String venue,
      UUID userId,
      String userName,
      String userEmail,
      EventRegistration.Status status,
      Instant checkedInAt,
      Instant checkedOutAt,
      BigDecimal hours,
      String notes
  ) {}

  public record SelfCheckInRequest(@NotBlank @Size(max = 16) String code) {}

  public record AttendanceNoteRequest(@Size(max = 2000) String notes) {}
}
