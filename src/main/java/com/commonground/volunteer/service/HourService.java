package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Event;
import com.commonground.volunteer.domain.EventRegistration;
import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.exception.NotFoundException;
import com.commonground.volunteer.repository.EventRepository;
import com.commonground.volunteer.repository.HourLogRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.HourDtos.HourLogRequest;
import com.commonground.volunteer.web.dto.HourDtos.HourLogResponse;
import com.commonground.volunteer.web.dto.HourDtos.ReviewRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HourService {

  private static final BigDecimal QUARTER = new BigDecimal("0.25");
  private static final BigDecimal MAX_HOURS = new BigDecimal("24");

  private final HourLogRepository hourLogRepository;
  private final EventRepository eventRepository;
  private final AccessGuard access;
  private final BadgeService badgeService;
  private final NotificationService notificationService;

  public HourService(
      HourLogRepository hourLogRepository,
      EventRepository eventRepository,
      AccessGuard access,
      BadgeService badgeService,
      NotificationService notificationService
  ) {
    this.hourLogRepository = hourLogRepository;
    this.eventRepository = eventRepository;
    this.access = access;
    this.badgeService = badgeService;
    this.notificationService = notificationService;
  }

  @Transactional(readOnly = true)
  public List<HourLogResponse> mine(UserPrincipal principal) {
    return hourLogRepository.findByUserIdOrderByWorkDateDescCreatedAtDesc(principal.id())
        .stream().map(HourService::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public List<HourLogResponse> organization(UserPrincipal principal, HourLog.Status status) {
    access.requireStaff(principal);
    UUID orgId = access.orgId(principal);
    List<HourLog> logs = status == null
        ? hourLogRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
        : hourLogRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(orgId, status);
    return logs.stream().map(HourService::toResponse).toList();
  }

  @Transactional
  public HourLogResponse submit(UserPrincipal principal, HourLogRequest request) {
    UUID orgId = access.orgId(principal);
    Event event = null;
    if (request.eventId() != null) {
      event = eventRepository.findByIdAndOrganizationId(request.eventId(), orgId)
          .orElseThrow(() -> new NotFoundException("Event not found"));
    }
    HourLog log = new HourLog();
    log.setOrganization(access.currentUser(principal).getOrganization());
    log.setUser(access.currentUser(principal));
    log.setEvent(event);
    log.setWorkDate(request.workDate());
    log.setHours(request.hours().setScale(2, RoundingMode.HALF_UP));
    log.setDescription(trimToNull(request.description()));
    log.setStatus(HourLog.Status.PENDING);
    log.setSource(HourLog.Source.MANUAL);
    return toResponse(hourLogRepository.save(log));
  }

  @Transactional
  public void deleteOwnPending(UserPrincipal principal, UUID id) {
    HourLog log = hourLogRepository.findByIdAndOrganizationId(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Hour log not found"));
    if (!log.getUser().getId().equals(principal.id())) {
      throw new ForbiddenException("You can only delete your own hour logs");
    }
    if (log.getStatus() != HourLog.Status.PENDING) {
      throw new ApiException("Only pending hour logs can be deleted");
    }
    hourLogRepository.delete(log);
  }

  @Transactional
  public HourLogResponse review(UserPrincipal principal, UUID id, ReviewRequest request) {
    access.requireStaff(principal);
    HourLog log = hourLogRepository.findByIdAndOrganizationId(id, access.orgId(principal))
        .orElseThrow(() -> new NotFoundException("Hour log not found"));
    if (log.getUser().getId().equals(principal.id())) {
      throw new ForbiddenException("You cannot review your own hours");
    }
    if (log.getStatus() != HourLog.Status.PENDING) {
      throw new ApiException("This hour log has already been reviewed");
    }

    UserAccount volunteer = log.getUser();
    BigDecimal before = hourLogRepository.sumApprovedHoursForUser(volunteer.getId());
    if (request.hours() != null) {
      log.setHours(request.hours().setScale(2, RoundingMode.HALF_UP));
    }
    log.setStatus(Boolean.TRUE.equals(request.approve()) ? HourLog.Status.APPROVED : HourLog.Status.REJECTED);
    log.setReviewedBy(access.currentUser(principal));
    log.setReviewedAt(Instant.now());
    log.setReviewNote(trimToNull(request.note()));
    hourLogRepository.flush();

    if (log.getStatus() == HourLog.Status.APPROVED) {
      notificationService.notify(volunteer, "HOURS_APPROVED", "Hours approved",
          log.getHours().stripTrailingZeros().toPlainString() + " hours on " + log.getWorkDate() + " were approved.",
          "/hours");
      announceBadges(volunteer, before);
    } else {
      notificationService.notify(volunteer, "HOURS_REJECTED", "Hours not approved",
          log.getReviewNote() != null ? log.getReviewNote() : "Your hour log for " + log.getWorkDate() + " was not approved.",
          "/hours");
    }
    return toResponse(log);
  }

  /**
   * Records attended hours when a volunteer checks out. Staff check-outs are approved immediately;
   * self check-outs wait for review.
   */
  @Transactional
  public HourLog recordAttendance(EventRegistration registration, UserAccount approvedBy) {
    Event event = registration.getEvent();
    UserAccount volunteer = registration.getUser();
    BigDecimal before = hourLogRepository.sumApprovedHoursForUser(volunteer.getId());

    HourLog log = new HourLog();
    log.setOrganization(event.getOrganization());
    log.setUser(volunteer);
    log.setEvent(event);
    log.setRegistration(registration);
    log.setWorkDate(event.getEventDate());
    log.setHours(attendedHours(registration.getCheckedInAt(), registration.getCheckedOutAt()));
    log.setDescription(event.getName());
    log.setSource(HourLog.Source.ATTENDANCE);
    if (approvedBy != null) {
      log.setStatus(HourLog.Status.APPROVED);
      log.setReviewedBy(approvedBy);
      log.setReviewedAt(Instant.now());
    } else {
      log.setStatus(HourLog.Status.PENDING);
    }
    log = hourLogRepository.saveAndFlush(log);

    if (log.getStatus() == HourLog.Status.APPROVED) {
      announceBadges(volunteer, before);
    }
    return log;
  }

  /** Attended time rounded to the nearest quarter hour, between 0.25 and 24. */
  static BigDecimal attendedHours(Instant in, Instant out) {
    if (in == null || out == null || !out.isAfter(in)) {
      return QUARTER;
    }
    BigDecimal hours = BigDecimal.valueOf(Duration.between(in, out).toMinutes())
        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
    BigDecimal quarters = hours.divide(QUARTER, 0, RoundingMode.HALF_UP);
    BigDecimal rounded = quarters.multiply(QUARTER).setScale(2, RoundingMode.HALF_UP);
    return rounded.max(QUARTER).min(MAX_HOURS);
  }

  private void announceBadges(UserAccount volunteer, BigDecimal before) {
    BigDecimal after = hourLogRepository.sumApprovedHoursForUser(volunteer.getId());
    for (BadgeService.Milestone m : badgeService.newlyEarned(before, after)) {
      notificationService.notify(volunteer, "BADGE_EARNED", "New badge: " + m.title(),
          "You reached " + m.hours() + (m.hours() == 1 ? " hour" : " hours") + " of volunteering.", "/badges");
    }
  }

  static HourLogResponse toResponse(HourLog log) {
    Event event = log.getEvent();
    UserAccount reviewer = log.getReviewedBy();
    return new HourLogResponse(
        log.getId(),
        log.getUser().getId(),
        log.getUser().getFullName(),
        event != null ? event.getId() : null,
        event != null ? event.getName() : null,
        log.getWorkDate(),
        log.getHours(),
        log.getDescription(),
        log.getStatus(),
        log.getSource(),
        reviewer != null ? reviewer.getFullName() : null,
        log.getReviewedAt(),
        log.getReviewNote(),
        log.getCreatedAt()
    );
  }

  static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
