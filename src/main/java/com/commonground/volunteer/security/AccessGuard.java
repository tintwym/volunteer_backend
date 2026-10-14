package com.commonground.volunteer.security;

import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.repository.UserAccountRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Organization scoping and role checks shared by the domain services. */
@Component
public class AccessGuard {

  private final UserAccountRepository userAccountRepository;

  public AccessGuard(UserAccountRepository userAccountRepository) {
    this.userAccountRepository = userAccountRepository;
  }

  public UUID orgId(UserPrincipal principal) {
    if (principal == null) {
      throw new ForbiddenException("Not signed in");
    }
    if (principal.organizationId() == null) {
      throw new ForbiddenException("You are not part of an organization");
    }
    return principal.organizationId();
  }

  public void requireRole(UserPrincipal principal, Role... allowed) {
    if (principal == null || Arrays.stream(allowed).noneMatch(r -> r == principal.role())) {
      throw new ForbiddenException("You do not have permission to do this");
    }
  }

  public void requireStaff(UserPrincipal principal) {
    requireRole(principal, Role.ORGANIZER, Role.VOLUNTEER_LEADER);
  }

  public boolean isStaff(UserPrincipal principal) {
    return principal.role() == Role.ORGANIZER || principal.role() == Role.VOLUNTEER_LEADER;
  }

  public UserAccount currentUser(UserPrincipal principal) {
    return userAccountRepository.getReferenceById(principal.id());
  }

  /** Loads a member of the caller's organization, hiding users from other organizations. */
  public UserAccount member(UserPrincipal principal, UUID userId) {
    return userAccountRepository.findByIdAndOrganizationId(userId, orgId(principal))
        .orElseThrow(() -> new com.commonground.volunteer.exception.NotFoundException("Member not found"));
  }
}
