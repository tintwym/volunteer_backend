package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.Incident;
import com.commonground.volunteer.domain.Message;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Tasks, incidents, messages and notifications. */
public final class WorkDtos {

  private WorkDtos() {}

  public record TaskRequest(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 5000) String description,
      Task.Priority priority,
      UUID assigneeId,
      LocalDate dueDate,
      UUID eventId
  ) {}

  public record TaskStatusRequest(@NotNull Task.Status status) {}

  public record TaskResponse(
      UUID id,
      String title,
      String description,
      Task.Priority priority,
      Task.Status status,
      UUID assigneeId,
      String assigneeName,
      LocalDate dueDate,
      UUID eventId,
      String eventName,
      String createdByName,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record IncidentRequest(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 5000) String description,
      @NotNull Incident.Severity severity,
      UUID eventId
  ) {}

  public record ResolveIncidentRequest(@NotBlank @Size(max = 5000) String resolution) {}

  public record IncidentResponse(
      UUID id,
      String title,
      String description,
      Incident.Severity severity,
      Incident.Status status,
      UUID eventId,
      String eventName,
      UUID reportedById,
      String reportedByName,
      String resolution,
      String resolvedByName,
      Instant resolvedAt,
      Instant createdAt
  ) {}

  public record MessageRequest(
      @NotBlank @Size(max = 4000) String content,
      boolean urgent
  ) {}

  public record MessageResponse(
      UUID id,
      Message.Channel channel,
      UUID senderId,
      String senderName,
      Role senderRole,
      String content,
      boolean urgent,
      Instant createdAt
  ) {}

  public record NotificationResponse(
      UUID id,
      String type,
      String title,
      String body,
      String link,
      boolean read,
      Instant createdAt
  ) {}

  public record NotificationList(long unreadCount, List<NotificationResponse> items) {}
}
