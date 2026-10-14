package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.EventRegistration;
import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.domain.Incident;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.Task;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.repository.EventRegistrationRepository;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.HourLogRepository;
import com.commonground.volunteer.repository.IncidentRepository;
import com.commonground.volunteer.repository.TaskRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.HourDtos.BadgeSummary;
import com.commonground.volunteer.web.dto.MemberDtos.MemberResponse;
import com.commonground.volunteer.web.dto.MemberDtos.MemberUpdateRequest;
import com.commonground.volunteer.web.dto.MemberDtos.MySummary;
import com.commonground.volunteer.web.dto.MemberDtos.OrganizerSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization members plus the dashboard summaries built on top of them. */
@Service
public class MemberService {

  private final UserAccountRepository userAccountRepository;
  private final HourLogRepository hourLogRepository;
  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final TaskRepository taskRepository;
  private final IncidentRepository incidentRepository;
  private final BadgeService badgeService;
  private final NotificationService notificationService;
  private final AccessGuard access;

  public MemberService(
      UserAccountRepository userAccountRepository,
      HourLogRepository hourLogRepository,
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      TaskRepository taskRepository,
      IncidentRepository incidentRepository,
      BadgeService badgeService,
      NotificationService notificationService,
      AccessGuard access
  ) {
    this.userAccountRepository = userAccountRepository;
    this.hourLogRepository = hourLogRepository;
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.taskRepository = taskRepository;
    this.incidentRepository = incidentRepository;
    this.badgeService = badgeService;
    this.notificationService = notificationService;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> list(UserPrincipal principal) {
    access.requireStaff(principal);
    UUID orgId = access.orgId(principal);
    Map<UUID, BigDecimal> hours = hourLogRepository.approvedHoursByUser(orgId).stream()
        .collect(Collectors.toMap(HourLogRepository.UserHours::getUserId, HourLogRepository.UserHours::getTotal));
    return userAccountRepository.findByOrganizationIdOrderByFullNameAsc(orgId).stream()
        .map(u -> toResponse(u, hours.getOrDefault(u.getId(), BigDecimal.ZERO)))
        .toList();
  }

  @Transactional
  public MemberResponse update(UserPrincipal principal, UUID memberId, MemberUpdateRequest request) {
    access.requireRole(principal, Role.ORGANIZER);
    UserAccount member = access.member(principal, memberId);
    if (member.getId().equals(principal.id())) {
      throw new ForbiddenException("You cannot change your own role or status");
    }
    if (member.getRole() == Role.ORGANIZER) {
      throw new ForbiddenException("Organizer accounts cannot be changed here");
    }
    if (request.role() != null) {
      if (request.role() == Role.ORGANIZER) {
        throw new ApiException("Members can only be volunteers or volunteer leaders");
      }
      if (request.role() != member.getRole()) {
        member.setRole(request.role());
        notificationService.notify(member, "ROLE_CHANGED", "Your role has changed",
            "You are now a " + (request.role() == Role.VOLUNTEER_LEADER ? "volunteer leader" : "volunteer") + ".",
            null);
      }
    }
    if (request.active() != null) {
      member.setActive(request.active());
    }
    return toResponse(member, hourLogRepository.sumApprovedHoursForUser(member.getId()));
  }

  @Transactional(readOnly = true)
  public OrganizerSummary organizerSummary(UserPrincipal principal) {
    access.requireStaff(principal);
    UUID orgId = access.orgId(principal);
    return new OrganizerSummary(
        hourLogRepository.sumApprovedHoursForOrganization(orgId),
        userAccountRepository.countByOrganizationIdAndRoleAndActiveTrue(orgId, Role.VOLUNTEER),
        userAccountRepository.countByOrganizationIdAndRoleAndActiveTrue(orgId, Role.VOLUNTEER_LEADER),
        eventRepository.countByOrganizationIdAndEventDateGreaterThanEqualAndStatusIn(
            orgId, LocalDate.now(), EnumSet.of(Event.Status.OPEN, Event.Status.CLOSED, Event.Status.IN_PROGRESS)),
        eventRepository.countByOrganizationIdAndStatus(orgId, Event.Status.COMPLETED),
        hourLogRepository.countByOrganizationIdAndStatus(orgId, HourLog.Status.PENDING),
        incidentRepository.countByOrganizationIdAndStatus(orgId, Incident.Status.OPEN),
        taskRepository.countByOrganizationIdAndStatusIn(orgId, List.of(Task.Status.TODO, Task.Status.IN_PROGRESS))
    );
  }

  @Transactional(readOnly = true)
  public MySummary mySummary(UserPrincipal principal) {
    access.orgId(principal);
    UUID userId = principal.id();
    BadgeSummary badges = badgeService.summary(userId);
    long openTasks = taskRepository.findByAssigneeIdOrderByCreatedAtDesc(userId).stream()
        .filter(t -> t.getStatus() == Task.Status.TODO || t.getStatus() == Task.Status.IN_PROGRESS)
        .count();
    return new MySummary(
        badges.totalHours(),
        hourLogRepository.countByUserIdAndStatus(userId, HourLog.Status.PENDING),
        registrationRepository.countByUserIdAndStatusIn(userId,
            EnumSet.of(EventRegistration.Status.CONFIRMED, EventRegistration.Status.WAITLIST)),
        registrationRepository.countByUserIdAndStatusIn(userId, EnumSet.of(EventRegistration.Status.COMPLETED)),
        openTasks,
        notificationService.unreadCount(userId),
        badges.current(),
        badges.next()
    );
  }

  private static MemberResponse toResponse(UserAccount u, BigDecimal hours) {
    return new MemberResponse(
        u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(), u.isActive(), hours, u.getCreatedAt());
  }
}
