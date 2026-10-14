package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.domain.Message;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.IncidentService;
import com.commonground.volunteer.service.MessageService;
import com.commonground.volunteer.service.TaskService;
import com.commonground.volunteer.web.dto.WorkDtos.IncidentRequest;
import com.commonground.volunteer.web.dto.WorkDtos.IncidentResponse;
import com.commonground.volunteer.web.dto.WorkDtos.MessageRequest;
import com.commonground.volunteer.web.dto.WorkDtos.MessageResponse;
import com.commonground.volunteer.web.dto.WorkDtos.ResolveIncidentRequest;
import com.commonground.volunteer.web.dto.WorkDtos.TaskRequest;
import com.commonground.volunteer.web.dto.WorkDtos.TaskResponse;
import com.commonground.volunteer.web.dto.WorkDtos.TaskStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tasks, incidents and messaging channels. */
@RestController
@RequestMapping("/api")
public class WorkController {

  private final TaskService taskService;
  private final IncidentService incidentService;
  private final MessageService messageService;

  public WorkController(TaskService taskService, IncidentService incidentService, MessageService messageService) {
    this.taskService = taskService;
    this.incidentService = incidentService;
    this.messageService = messageService;
  }

  @GetMapping("/tasks")
  public List<TaskResponse> tasks(@AuthenticationPrincipal UserPrincipal principal) {
    return taskService.list(principal);
  }

  @PostMapping("/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  public TaskResponse createTask(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TaskRequest request
  ) {
    return taskService.create(principal, request);
  }

  @PutMapping("/tasks/{id}")
  public TaskResponse updateTask(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody TaskRequest request
  ) {
    return taskService.update(principal, id, request);
  }

  @PatchMapping("/tasks/{id}/status")
  public TaskResponse updateTaskStatus(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody TaskStatusRequest request
  ) {
    return taskService.updateStatus(principal, id, request.status());
  }

  @DeleteMapping("/tasks/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTask(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    taskService.delete(principal, id);
  }

  @GetMapping("/incidents")
  public List<IncidentResponse> incidents(@AuthenticationPrincipal UserPrincipal principal) {
    return incidentService.list(principal);
  }

  @PostMapping("/incidents")
  @ResponseStatus(HttpStatus.CREATED)
  public IncidentResponse reportIncident(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody IncidentRequest request
  ) {
    return incidentService.report(principal, request);
  }

  @PostMapping("/incidents/{id}/resolve")
  public IncidentResponse resolveIncident(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody ResolveIncidentRequest request
  ) {
    return incidentService.resolve(principal, id, request.resolution());
  }

  @GetMapping("/messages/{channel}")
  public List<MessageResponse> messages(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Message.Channel channel,
      @RequestParam(defaultValue = "50") int limit
  ) {
    return messageService.list(principal, channel, limit);
  }

  @PostMapping("/messages/{channel}")
  @ResponseStatus(HttpStatus.CREATED)
  public MessageResponse postMessage(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Message.Channel channel,
      @Valid @RequestBody MessageRequest request
  ) {
    return messageService.post(principal, channel, request);
  }
}
