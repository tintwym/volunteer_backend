package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.BadgeService;
import com.commonground.volunteer.service.DeviceTokenService;
import com.commonground.volunteer.service.EventService;
import com.commonground.volunteer.service.MemberService;
import com.commonground.volunteer.service.NotificationService;
import com.commonground.volunteer.service.TaskService;
import com.commonground.volunteer.web.dto.DeviceTokenDtos.DeviceTokenRequest;
import com.commonground.volunteer.web.dto.DeviceTokenDtos.DeviceTokenResponse;
import com.commonground.volunteer.web.dto.EventDtos.RegistrationResponse;
import com.commonground.volunteer.web.dto.HourDtos.BadgeSummary;
import com.commonground.volunteer.web.dto.MemberDtos.MySummary;
import com.commonground.volunteer.web.dto.WorkDtos.NotificationList;
import com.commonground.volunteer.web.dto.WorkDtos.NotificationResponse;
import com.commonground.volunteer.web.dto.WorkDtos.TaskResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints scoped to the signed-in user. */
@RestController
@RequestMapping("/api/me")
public class MeController {

  private final MemberService memberService;
  private final BadgeService badgeService;
  private final EventService eventService;
  private final TaskService taskService;
  private final NotificationService notificationService;
  private final DeviceTokenService deviceTokenService;

  public MeController(
      MemberService memberService,
      BadgeService badgeService,
      EventService eventService,
      TaskService taskService,
      NotificationService notificationService,
      DeviceTokenService deviceTokenService
  ) {
    this.memberService = memberService;
    this.badgeService = badgeService;
    this.eventService = eventService;
    this.taskService = taskService;
    this.notificationService = notificationService;
    this.deviceTokenService = deviceTokenService;
  }

  @GetMapping("/summary")
  public MySummary summary(@AuthenticationPrincipal UserPrincipal principal) {
    return memberService.mySummary(principal);
  }

  @GetMapping("/badges")
  public BadgeSummary badges(@AuthenticationPrincipal UserPrincipal principal) {
    return badgeService.summary(principal.id());
  }

  @GetMapping("/registrations")
  public List<RegistrationResponse> registrations(@AuthenticationPrincipal UserPrincipal principal) {
    return eventService.myRegistrations(principal);
  }

  @GetMapping("/tasks")
  public List<TaskResponse> tasks(@AuthenticationPrincipal UserPrincipal principal) {
    return taskService.mine(principal);
  }

  @GetMapping("/notifications")
  public NotificationList notifications(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "30") int limit
  ) {
    return notificationService.list(principal, limit);
  }

  @PostMapping("/notifications/{id}/read")
  public NotificationResponse markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return notificationService.markRead(principal, id);
  }

  @PostMapping("/notifications/read-all")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
    notificationService.markAllRead(principal);
  }

  @PostMapping("/devices")
  public DeviceTokenResponse registerDevice(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestBody DeviceTokenRequest request
  ) {
    return deviceTokenService.upsert(principal, request);
  }

  @DeleteMapping("/devices")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregisterDevice(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam String token
  ) {
    deviceTokenService.remove(principal, token);
  }
}
