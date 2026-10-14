package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.domain.HourLog;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.HourService;
import com.commonground.volunteer.web.dto.HourDtos.HourLogRequest;
import com.commonground.volunteer.web.dto.HourDtos.HourLogResponse;
import com.commonground.volunteer.web.dto.HourDtos.ReviewRequest;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/hours")
public class HourController {

  private final HourService hourService;

  public HourController(HourService hourService) {
    this.hourService = hourService;
  }

  @GetMapping("/mine")
  public List<HourLogResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
    return hourService.mine(principal);
  }

  /** Staff view of the organization's hour logs, optionally filtered by status (e.g. PENDING). */
  @GetMapping
  public List<HourLogResponse> organization(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) HourLog.Status status
  ) {
    return hourService.organization(principal, status);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public HourLogResponse submit(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody HourLogRequest request
  ) {
    return hourService.submit(principal, request);
  }

  @PostMapping("/{id}/review")
  public HourLogResponse review(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody ReviewRequest request
  ) {
    return hourService.review(principal, id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    hourService.deleteOwnPending(principal, id);
  }
}
