package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.Incident;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.exception.NotFoundException;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.IncidentRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.WorkDtos.IncidentRequest;
import com.commonground.volunteer.web.dto.WorkDtos.IncidentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

  private final IncidentRepository incidentRepository;
  private final EventRepository eventRepository;
  private final UserAccountRepository userAccountRepository;
  private final NotificationService notificationService;
  private final AccessGuard access;

  public IncidentService(
      IncidentRepository incidentRepository,
      EventRepository eventRepository,
      UserAccountRepository userAccountRepository,
      NotificationService notificationService,
      AccessGuard access
  ) {
    this.incidentRepository = incidentRepository;
    this.eventRepository = eventRepository;
    this.userAccountRepository = userAccountRepository;
    this.notificationService = notificationService;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<IncidentResponse> list(UserPrincipal principal) {
    UUID orgId = access.orgId(principal);
    List<Incident> incidents = access.isStaff(principal)
        ? incidentRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
        : incidentRepository.findByReportedByIdOrderByCreatedAtDesc(principal.id());
    return incidents.stream().map(IncidentService::toResponse).toList();
  }

  @Transactional
  public IncidentResponse report(UserPrincipal principal, IncidentRequest request) {
    UUID orgId = access.orgId(principal);
    UserAccount reporter = access.currentUser(principal);
    Incident incident = new Incident();
    incident.setOrganization(reporter.getOrganization());
    incident.setReportedBy(reporter);
    incident.setTitle(request.title().trim());
    incident.setDescription(request.description().trim());
    incident.setSeverity(request.severity());
    incident.setStatus(Incident.Status.OPEN);
    if (request.eventId() != null) {
      Event event = eventRepository.findByIdAndOrganizationId(request.eventId(), orgId)
          .orElseThrow(() -> new NotFoundException("Event not found"));
      incident.setEvent(event);
    }
    incident = incidentRepository.save(incident);

    List<UserAccount> staff = userAccountRepository
        .findByOrganizationIdAndRoleInAndActiveTrue(orgId, List.of(Role.ORGANIZER, Role.VOLUNTEER_LEADER))
        .stream().filter(u -> !u.getId().equals(principal.id())).toList();
    notificationService.notifyAll(staff, "INCIDENT_REPORTED",
        "[" + incident.getSeverity() + "] " + incident.getTitle(),
        "Reported by " + reporter.getFullName(), "/incidents");
    return toResponse(incident);
  }

  @Transactional
  public IncidentResponse resolve(UserPrincipal principal, UUID id, String resolution) {
    access.requireStaff(principal);
    Incident incident = incidentRepository.findByIdAndOrganizationId(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Incident not found"));
    if (incident.getStatus() == Incident.Status.RESOLVED) {
      throw new ApiException("Incident is already resolved");
    }
    incident.setStatus(Incident.Status.RESOLVED);
    incident.setResolution(resolution.trim());
    incident.setResolvedBy(access.currentUser(principal));
    incident.setResolvedAt(Instant.now());
    if (incident.getReportedBy() != null && !incident.getReportedBy().getId().equals(principal.id())) {
      notificationService.notify(incident.getReportedBy(), "INCIDENT_RESOLVED",
          "Incident resolved: " + incident.getTitle(), incident.getResolution(), "/incidents");
    }
    return toResponse(incident);
  }

  private static IncidentResponse toResponse(Incident i) {
    Event event = i.getEvent();
    UserAccount reporter = i.getReportedBy();
    return new IncidentResponse(
        i.getId(), i.getTitle(), i.getDescription(), i.getSeverity(), i.getStatus(),
        event != null ? event.getId() : null,
        event != null ? event.getName() : null,
        reporter != null ? reporter.getId() : null,
        reporter != null ? reporter.getFullName() : null,
        i.getResolution(),
        i.getResolvedBy() != null ? i.getResolvedBy().getFullName() : null,
        i.getResolvedAt(), i.getCreatedAt());
  }
}
