package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.InvitationService;
import com.commonground.volunteer.web.dto.InviteRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

  private final InvitationService invitationService;

  public InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ORGANIZER')")
  public Map<String, Object> invite(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody InviteRequest request
  ) {
    return invitationService.invite(principal, request);
  }
}
