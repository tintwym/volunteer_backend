package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.EventService;
import com.commonground.volunteer.web.dto.EventDtos.EventRequest;
import com.commonground.volunteer.web.dto.EventDtos.EventResponse;
import com.commonground.volunteer.web.dto.EventDtos.RegistrationResponse;
import com.commonground.volunteer.web.dto.EventDtos.SelfCheckInRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @GetMapping
  public List<EventResponse> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "false") boolean upcoming
  ) {
    return eventService.list(principal, upcoming);
  }

  @GetMapping("/{id}")
  public EventResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return eventService.get(principal, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EventResponse create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody EventRequest request
  ) {
    return eventService.create(principal, request);
  }

  @PutMapping("/{id}")
  public EventResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody EventRequest request
  ) {
    return eventService.update(principal, id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    eventService.delete(principal, id);
  }

  @PostMapping("/{id}/check-in-code")
  public EventResponse regenerateCode(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return eventService.regenerateCheckInCode(principal, id);
  }

  @PostMapping("/{id}/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResponse register(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return eventService.register(principal, id);
  }

  @DeleteMapping("/{id}/register")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    eventService.cancel(principal, id);
  }

  @PostMapping("/{id}/check-in")
  public RegistrationResponse selfCheckIn(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody SelfCheckInRequest request
  ) {
    return eventService.selfCheckIn(principal, id, request.code());
  }

  @PostMapping("/{id}/check-out")
  public RegistrationResponse selfCheckOut(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    return eventService.selfCheckOut(principal, id);
  }

  @GetMapping("/{id}/registrations")
  public List<RegistrationResponse> registrations(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id
  ) {
    return eventService.registrations(principal, id);
  }

  @PostMapping("/{id}/registrations/{registrationId}/check-in")
  public RegistrationResponse staffCheckIn(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @PathVariable UUID registrationId
  ) {
    return eventService.staffCheckIn(principal, id, registrationId);
  }

  @PostMapping("/{id}/registrations/{registrationId}/check-out")
  public RegistrationResponse staffCheckOut(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @PathVariable UUID registrationId
  ) {
    return eventService.staffCheckOut(principal, id, registrationId);
  }

  @PostMapping("/{id}/registrations/{registrationId}/no-show")
  public RegistrationResponse noShow(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @PathVariable UUID registrationId
  ) {
    return eventService.markNoShow(principal, id, registrationId);
  }
}
