package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.Task;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.exception.NotFoundException;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.TaskRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.WorkDtos.TaskRequest;
import com.commonground.volunteer.web.dto.WorkDtos.TaskResponse;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private final TaskRepository taskRepository;
  private final EventRepository eventRepository;
  private final NotificationService notificationService;
  private final AccessGuard access;

  public TaskService(
      TaskRepository taskRepository,
      EventRepository eventRepository,
      NotificationService notificationService,
      AccessGuard access
  ) {
    this.taskRepository = taskRepository;
    this.eventRepository = eventRepository;
    this.notificationService = notificationService;
    this.access = access;
  }

  /** Staff see every task in the organization; volunteers only see tasks assigned to them. */
  @Transactional(readOnly = true)
  public List<TaskResponse> list(UserPrincipal principal) {
    UUID orgId = access.orgId(principal);
    List<Task> tasks = access.isStaff(principal)
        ? taskRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
        : taskRepository.findByAssigneeIdOrderByCreatedAtDesc(principal.id());
    return tasks.stream().map(TaskService::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<TaskResponse> mine(UserPrincipal principal) {
    access.orgId(principal);
    return taskRepository.findByAssigneeIdOrderByCreatedAtDesc(principal.id()).stream()
        .map(TaskService::toResponse).toList();
  }

  @Transactional
  public TaskResponse create(UserPrincipal principal, TaskRequest request) {
    access.requireStaff(principal);
    UserAccount creator = access.currentUser(principal);
    Task task = new Task();
    task.setOrganization(creator.getOrganization());
    task.setCreatedBy(creator);
    task.setStatus(Task.Status.TODO);
    apply(principal, task, request);
    task = taskRepository.save(task);
    notifyAssignee(task, null);
    return toResponse(task);
  }

  @Transactional
  public TaskResponse update(UserPrincipal principal, UUID id, TaskRequest request) {
    access.requireStaff(principal);
    Task task = orgTask(principal, id);
    UUID previousAssignee = task.getAssignee() != null ? task.getAssignee().getId() : null;
    apply(principal, task, request);
    notifyAssignee(task, previousAssignee);
    return toResponse(task);
  }

  @Transactional
  public TaskResponse updateStatus(UserPrincipal principal, UUID id, Task.Status status) {
    Task task = orgTask(principal, id);
    boolean assignee = task.getAssignee() != null && task.getAssignee().getId().equals(principal.id());
    if (!assignee && !access.isStaff(principal)) {
      throw new ForbiddenException("You can only update tasks assigned to you");
    }
    if (status == Task.Status.CANCELLED && !access.isStaff(principal)) {
      throw new ForbiddenException("Only organizers and leaders can cancel tasks");
    }
    task.setStatus(status);
    return toResponse(task);
  }

  @Transactional
  public void delete(UserPrincipal principal, UUID id) {
    Task task = orgTask(principal, id);
    boolean creator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(principal.id());
    if (principal.role() != Role.ORGANIZER && !creator) {
      throw new ForbiddenException("Only organizers or the task creator can delete a task");
    }
    taskRepository.delete(task);
  }

  private void apply(UserPrincipal principal, Task task, TaskRequest request) {
    task.setTitle(request.title().trim());
    task.setDescription(HourService.trimToNull(request.description()));
    task.setPriority(request.priority() != null ? request.priority() : Task.Priority.MEDIUM);
    task.setDueDate(request.dueDate());
    task.setAssignee(request.assigneeId() != null ? access.member(principal, request.assigneeId()) : null);
    Event event = null;
    if (request.eventId() != null) {
      event = eventRepository.findByIdAndOrganizationId(request.eventId(), access.orgId(principal))
          .orElseThrow(() -> new NotFoundException("Event not found"));
    }
    task.setEvent(event);
  }

  private void notifyAssignee(Task task, UUID previousAssignee) {
    UserAccount assignee = task.getAssignee();
    if (assignee == null || Objects.equals(assignee.getId(), previousAssignee)) {
      return;
    }
    notificationService.notify(assignee, "TASK_ASSIGNED", "New task: " + task.getTitle(),
        task.getDueDate() != null ? "Due " + task.getDueDate() : null, "/tasks");
  }

  private Task orgTask(UserPrincipal principal, UUID id) {
    return taskRepository.findByIdAndOrganizationId(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Task not found"));
  }

  private static TaskResponse toResponse(Task t) {
    UserAccount assignee = t.getAssignee();
    Event event = t.getEvent();
    return new TaskResponse(
        t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
        assignee != null ? assignee.getId() : null,
        assignee != null ? assignee.getFullName() : null,
        t.getDueDate(),
        event != null ? event.getId() : null,
        event != null ? event.getName() : null,
        t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null,
        t.getCreatedAt(), t.getUpdatedAt());
  }
}
