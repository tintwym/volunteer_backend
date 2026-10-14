package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.EventRegistration;
import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.exception.NotFoundException;
import com.commonground.volunteer.repository.EventRegistrationRepository;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.HourLogRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.EventDtos.EventRequest;
import com.commonground.volunteer.web.dto.EventDtos.EventResponse;
import com.commonground.volunteer.web.dto.EventDtos.RegistrationResponse;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

  private static final Set<Event.Status> VISIBLE_TO_VOLUNTEERS = EnumSet.complementOf(EnumSet.of(Event.Status.DRAFT));
  private static final Set<Event.Status> CHECK_IN_ALLOWED =
      EnumSet.of(Event.Status.OPEN, Event.Status.CLOSED, Event.Status.IN_PROGRESS);
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final HourLogRepository hourLogRepository;
  private final HourService hourService;
  private final NotificationService notificationService;
  private final AccessGuard access;

  public EventService(
      EventRepository eventRepository,
      EventRegistrationRepository registrationRepository,
      HourLogRepository hourLogRepository,
      HourService hourService,
      NotificationService notificationService,
      AccessGuard access
  ) {
    this.eventRepository = eventRepository;
    this.registrationRepository = registrationRepository;
    this.hourLogRepository = hourLogRepository;
    this.hourService = hourService;
    this.notificationService = notificationService;
    this.access = access;
  }

  // ---------------------------------------------------------------- events

  @Transactional(readOnly = true)
  public List<EventResponse> list(UserPrincipal principal, boolean upcomingOnly) {
    UUID orgId = access.orgId(principal);
    List<Event> events = access.isStaff(principal)
        ? eventRepository.findByOrganizationIdOrderByEventDateAscStartTimeAsc(orgId)
        : eventRepository.findByOrganizationIdAndStatusInOrderByEventDateAscStartTimeAsc(orgId, VISIBLE_TO_VOLUNTEERS);
    if (upcomingOnly) {
      LocalDate today = LocalDate.now();
      events = events.stream().filter(e -> !e.getEventDate().isBefore(today)).toList();
    }
    return toResponses(principal, events);
  }

  @Transactional(readOnly = true)
  public EventResponse get(UserPrincipal principal, UUID id) {
    return toResponses(principal, List.of(visibleEvent(principal, id))).get(0);
  }

  @Transactional
  public EventResponse create(UserPrincipal principal, EventRequest request) {
    access.requireRole(principal, Role.ORGANIZER);
    UserAccount creator = access.currentUser(principal);
    Event event = new Event();
    event.setOrganization(creator.getOrganization());
    event.setCreatedBy(creator);
    event.setCheckInCode(newCheckInCode());
    apply(event, request);
    if (event.getStatus() == null) {
      event.setStatus(Event.Status.OPEN);
    }
    return toResponses(principal, List.of(eventRepository.save(event))).get(0);
  }

  @Transactional
  public EventResponse update(UserPrincipal principal, UUID id, EventRequest request) {
    access.requireRole(principal, Role.ORGANIZER);
    Event event = eventRepository.findForUpdate(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Event not found"));
    Event.Status previous = event.getStatus();
    int previousCapacity = event.getCapacity();
    apply(event, request);
    if (event.getStatus() == null) {
      event.setStatus(previous);
    }
    if (event.getCapacity() > previousCapacity && event.getStatus() == Event.Status.OPEN) {
      promoteFromWaitlist(event);
    }
    if (previous != Event.Status.CANCELLED && event.getStatus() == Event.Status.CANCELLED) {
      notifyRegistrants(event, "EVENT_CANCELLED", "Event cancelled: " + event.getName(),
          event.getName() + " on " + event.getEventDate() + " has been cancelled.");
    }
    return toResponses(principal, List.of(event)).get(0);
  }

  @Transactional
  public void delete(UserPrincipal principal, UUID id) {
    access.requireRole(principal, Role.ORGANIZER);
    Event event = orgEvent(principal, id);
    if (event.getStatus() != Event.Status.CANCELLED && event.getStatus() != Event.Status.COMPLETED) {
      notifyRegistrants(event, "EVENT_CANCELLED", "Event cancelled: " + event.getName(),
          event.getName() + " on " + event.getEventDate() + " has been removed.");
    }
    eventRepository.delete(event);
  }

  @Transactional
  public EventResponse regenerateCheckInCode(UserPrincipal principal, UUID id) {
    access.requireStaff(principal);
    Event event = orgEvent(principal, id);
    event.setCheckInCode(newCheckInCode());
    return toResponses(principal, List.of(event)).get(0);
  }

  // --------------------------------------------------------- registrations

  @Transactional
  public RegistrationResponse register(UserPrincipal principal, UUID eventId) {
    UUID orgId = access.orgId(principal);
    Event event = eventRepository.findForUpdate(eventId, orgId)
        .orElseThrow(() -> new NotFoundException("Event not found"));
    if (event.getStatus() != Event.Status.OPEN) {
      throw new ApiException("This event is not open for registration");
    }

    EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, principal.id())
        .orElse(null);
    if (registration != null && registration.getStatus() != EventRegistration.Status.CANCELLED) {
      throw new ApiException("You are already registered for this event");
    }
    if (registration == null) {
      registration = new EventRegistration();
      registration.setEvent(event);
      registration.setUser(access.currentUser(principal));
    }

    long seated = registrationRepository.countByEventIdAndStatusIn(eventId, EventRegistration.Status.HOLDS_SEAT);
    boolean confirmed = seated < event.getCapacity();
    registration.setStatus(confirmed ? EventRegistration.Status.CONFIRMED : EventRegistration.Status.WAITLIST);
    registration.setCheckedInAt(null);
    registration.setCheckedOutAt(null);
    registration = registrationRepository.save(registration);

    notificationService.notify(registration.getUser(),
        confirmed ? "REGISTRATION_CONFIRMED" : "REGISTRATION_WAITLISTED",
        confirmed ? "You're registered: " + event.getName() : "You're on the waitlist: " + event.getName(),
        event.getEventDate() + " " + event.getStartTime() + "–" + event.getEndTime(),
        "/events/" + event.getId());
    return toRegistration(registration, null);
  }

  @Transactional
  public void cancel(UserPrincipal principal, UUID eventId) {
    UUID orgId = access.orgId(principal);
    Event event = eventRepository.findForUpdate(eventId, orgId)
        .orElseThrow(() -> new NotFoundException("Event not found"));
    EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, principal.id())
        .orElseThrow(() -> new NotFoundException("You are not registered for this event"));

    EventRegistration.Status status = registration.getStatus();
    if (status != EventRegistration.Status.CONFIRMED && status != EventRegistration.Status.WAITLIST) {
      throw new ApiException("This registration can no longer be cancelled");
    }
    registration.setStatus(EventRegistration.Status.CANCELLED);
    registrationRepository.flush();
    if (status == EventRegistration.Status.CONFIRMED) {
      promoteFromWaitlist(event);
    }
  }

  @Transactional(readOnly = true)
  public List<RegistrationResponse> registrations(UserPrincipal principal, UUID eventId) {
    access.requireStaff(principal);
    orgEvent(principal, eventId);
    List<EventRegistration> regs = registrationRepository.findByEventIdOrderByCreatedAtAsc(eventId);
    return regs.stream().map(r -> toRegistration(r, attendanceHours(r))).toList();
  }

  @Transactional(readOnly = true)
  public List<RegistrationResponse> myRegistrations(UserPrincipal principal) {
    access.orgId(principal);
    return registrationRepository.findByUserIdOrderByCreatedAtDesc(principal.id()).stream()
        .map(r -> toRegistration(r, attendanceHours(r)))
        .toList();
  }

  // ------------------------------------------------------------ attendance

  @Transactional
  public RegistrationResponse selfCheckIn(UserPrincipal principal, UUID eventId, String code) {
    Event event = visibleEvent(principal, eventId);
    EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, principal.id())
        .orElseThrow(() -> new ApiException("You are not registered for this event"));
    if (!event.getCheckInCode().equalsIgnoreCase(code.trim())) {
      throw new ApiException("Incorrect check-in code");
    }
    LocalDate today = LocalDate.now();
    if (event.getEventDate().isBefore(today.minusDays(1)) || event.getEventDate().isAfter(today.plusDays(1))) {
      throw new ApiException("Check-in is only available on the day of the event");
    }
    checkIn(event, registration);
    return toRegistration(registration, null);
  }

  @Transactional
  public RegistrationResponse selfCheckOut(UserPrincipal principal, UUID eventId) {
    visibleEvent(principal, eventId);
    EventRegistration registration = registrationRepository.findByEventIdAndUserId(eventId, principal.id())
        .orElseThrow(() -> new ApiException("You are not registered for this event"));
    HourLog log = checkOut(registration, null);
    return toRegistration(registration, log.getHours());
  }

  @Transactional
  public RegistrationResponse staffCheckIn(UserPrincipal principal, UUID eventId, UUID registrationId) {
    access.requireStaff(principal);
    Event event = orgEvent(principal, eventId);
    EventRegistration registration = registrationOf(eventId, registrationId);
    checkIn(event, registration);
    return toRegistration(registration, null);
  }

  @Transactional
  public RegistrationResponse staffCheckOut(UserPrincipal principal, UUID eventId, UUID registrationId) {
    access.requireStaff(principal);
    orgEvent(principal, eventId);
    EventRegistration registration = registrationOf(eventId, registrationId);
    if (registration.getUser().getId().equals(principal.id())) {
      throw new ForbiddenException("You cannot approve your own attendance");
    }
    HourLog log = checkOut(registration, access.currentUser(principal));
    notificationService.notify(registration.getUser(), "HOURS_APPROVED", "Attendance recorded",
        log.getHours().stripTrailingZeros().toPlainString() + " hours for " + registration.getEvent().getName()
            + " were added to your total.",
        "/hours");
    return toRegistration(registration, log.getHours());
  }

  @Transactional
  public RegistrationResponse markNoShow(UserPrincipal principal, UUID eventId, UUID registrationId) {
    access.requireStaff(principal);
    orgEvent(principal, eventId);
    EventRegistration registration = registrationOf(eventId, registrationId);
    if (registration.getStatus() != EventRegistration.Status.CONFIRMED) {
      throw new ApiException("Only confirmed registrations can be marked as no-show");
    }
    registration.setStatus(EventRegistration.Status.NO_SHOW);
    return toRegistration(registration, null);
  }

  private void checkIn(Event event, EventRegistration registration) {
    if (!CHECK_IN_ALLOWED.contains(event.getStatus())) {
      throw new ApiException("Check-in is not available for this event");
    }
    EventRegistration.Status status = registration.getStatus();
    if (status == EventRegistration.Status.CHECKED_IN) {
      throw new ApiException("Already checked in");
    }
    if (status != EventRegistration.Status.CONFIRMED && status != EventRegistration.Status.NO_SHOW) {
      throw new ApiException("Only confirmed volunteers can check in");
    }
    registration.setStatus(EventRegistration.Status.CHECKED_IN);
    registration.setCheckedInAt(Instant.now());
  }

  private HourLog checkOut(EventRegistration registration, UserAccount approvedBy) {
    if (registration.getStatus() != EventRegistration.Status.CHECKED_IN) {
      throw new ApiException("Volunteer is not checked in");
    }
    registration.setStatus(EventRegistration.Status.COMPLETED);
    registration.setCheckedOutAt(Instant.now());
    registrationRepository.flush();
    return hourService.recordAttendance(registration, approvedBy);
  }

  // --------------------------------------------------------------- helpers

  /** Fills every free seat from the waitlist, oldest first. */
  private void promoteFromWaitlist(Event event) {
    long seated = registrationRepository.countByEventIdAndStatusIn(event.getId(), EventRegistration.Status.HOLDS_SEAT);
    while (seated < event.getCapacity()) {
      EventRegistration next = registrationRepository
          .findFirstByEventIdAndStatusOrderByUpdatedAtAsc(event.getId(), EventRegistration.Status.WAITLIST)
          .orElse(null);
      if (next == null) {
        return;
      }
      next.setStatus(EventRegistration.Status.CONFIRMED);
      registrationRepository.flush();
      seated++;
      notificationService.notify(next.getUser(), "REGISTRATION_CONFIRMED",
          "A spot opened up: " + event.getName(),
          "You've been moved from the waitlist to confirmed.", "/events/" + event.getId());
    }
  }

  private void notifyRegistrants(Event event, String type, String title, String body) {
    List<UserAccount> users = registrationRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).stream()
        .filter(r -> r.getStatus() == EventRegistration.Status.CONFIRMED
            || r.getStatus() == EventRegistration.Status.WAITLIST)
        .map(EventRegistration::getUser)
        .toList();
    notificationService.notifyAll(users, type, title, body, "/events");
  }

  private Event orgEvent(UserPrincipal principal, UUID id) {
    return eventRepository.findByIdAndOrganizationId(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Event not found"));
  }

  private Event visibleEvent(UserPrincipal principal, UUID id) {
    Event event = orgEvent(principal, id);
    if (!access.isStaff(principal) && !VISIBLE_TO_VOLUNTEERS.contains(event.getStatus())) {
      throw new NotFoundException("Event not found");
    }
    return event;
  }

  private EventRegistration registrationOf(UUID eventId, UUID registrationId) {
    return registrationRepository.findByIdAndEventId(registrationId, eventId)
        .orElseThrow(() -> new NotFoundException("Registration not found"));
  }

  private BigDecimal attendanceHours(EventRegistration registration) {
    if (registration.getStatus() != EventRegistration.Status.COMPLETED) {
      return null;
    }
    return hourLogRepository.findByRegistrationId(registration.getId())
        .map(HourLog::getHours)
        .orElse(null);
  }

  private void apply(Event event, EventRequest request) {
    if (!request.endTime().isAfter(request.startTime())) {
      throw new ApiException("End time must be after start time");
    }
    event.setName(request.name().trim());
    event.setDescription(HourService.trimToNull(request.description()));
    event.setCategory(HourService.trimToNull(request.category()));
    event.setVenue(HourService.trimToNull(request.venue()));
    event.setAddress(HourService.trimToNull(request.address()));
    event.setRemote(Boolean.TRUE.equals(request.remote()));
    event.setMeetingLink(HourService.trimToNull(request.meetingLink()));
    event.setEventDate(request.date());
    event.setStartTime(request.startTime());
    event.setEndTime(request.endTime());
    event.setCapacity(request.capacity());
    event.setImageUrl(HourService.trimToNull(request.imageUrl()));
    if (request.status() != null) {
      event.setStatus(request.status());
    }
  }

  private List<EventResponse> toResponses(UserPrincipal principal, List<Event> events) {
    if (events.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = events.stream().map(Event::getId).toList();
    Map<UUID, Long> seated = counts(ids, EventRegistration.Status.HOLDS_SEAT);
    Map<UUID, Long> waitlisted = counts(ids, EnumSet.of(EventRegistration.Status.WAITLIST));
    Map<UUID, EventRegistration.Status> mine = new HashMap<>();
    for (EventRegistration r : registrationRepository.findByUserIdOrderByCreatedAtDesc(principal.id())) {
      mine.putIfAbsent(r.getEvent().getId(), r.getStatus());
    }
    boolean staff = access.isStaff(principal);
    return events.stream().map(e -> new EventResponse(
        e.getId(),
        e.getName(),
        e.getDescription(),
        e.getCategory(),
        e.getVenue(),
        e.getAddress(),
        e.isRemote(),
        e.getMeetingLink(),
        e.getEventDate(),
        e.getStartTime(),
        e.getEndTime(),
        Duration.between(e.getStartTime(), e.getEndTime()).toMinutes() / 60.0,
        e.getCapacity(),
        seated.getOrDefault(e.getId(), 0L),
        waitlisted.getOrDefault(e.getId(), 0L),
        e.getStatus(),
        e.getImageUrl(),
        staff ? e.getCheckInCode() : null,
        mine.get(e.getId()),
        e.getCreatedAt()
    )).toList();
  }

  private Map<UUID, Long> counts(List<UUID> eventIds, Set<EventRegistration.Status> statuses) {
    return registrationRepository.countByEventIds(eventIds, statuses).stream()
        .collect(Collectors.toMap(
            EventRegistrationRepository.EventCount::getEventId,
            EventRegistrationRepository.EventCount::getTotal));
  }

  private static RegistrationResponse toRegistration(EventRegistration r, BigDecimal hours) {
    Event e = r.getEvent();
    UserAccount u = r.getUser();
    return new RegistrationResponse(
        r.getId(), e.getId(), e.getName(), e.getEventDate(), e.getStartTime(), e.getEndTime(), e.getVenue(),
        u.getId(), u.getFullName(), u.getEmail(), r.getStatus(), r.getCheckedInAt(), r.getCheckedOutAt(),
        hours, r.getNotes());
  }

  private static String newCheckInCode() {
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    }
    return sb.toString();
  }
}
