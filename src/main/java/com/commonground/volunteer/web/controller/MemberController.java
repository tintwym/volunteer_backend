package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.BadgeService;
import com.commonground.volunteer.service.MemberService;
import com.commonground.volunteer.web.dto.HourDtos.BadgeSummary;
import com.commonground.volunteer.web.dto.MemberDtos.MemberResponse;
import com.commonground.volunteer.web.dto.MemberDtos.MemberUpdateRequest;
import com.commonground.volunteer.web.dto.MemberDtos.OrganizerSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MemberController {

  private final MemberService memberService;
  private final BadgeService badgeService;
  private final AccessGuard access;

  public MemberController(MemberService memberService, BadgeService badgeService, AccessGuard access) {
    this.memberService = memberService;
    this.badgeService = badgeService;
    this.access = access;
  }

  @GetMapping("/members")
  public List<MemberResponse> members(@AuthenticationPrincipal UserPrincipal principal) {
    return memberService.list(principal);
  }

  @PatchMapping("/members/{id}")
  public MemberResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @RequestBody MemberUpdateRequest request
  ) {
    return memberService.update(principal, id, request);
  }

  @GetMapping("/members/{id}/badges")
  public BadgeSummary memberBadges(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    access.requireStaff(principal);
    return badgeService.summary(access.member(principal, id).getId());
  }

  @GetMapping("/dashboard/organization")
  public OrganizerSummary organizationSummary(@AuthenticationPrincipal UserPrincipal principal) {
    return memberService.organizerSummary(principal);
  }
}
