package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Invitation;
import com.commonground.volunteer.domain.Organization;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.repository.InvitationRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.AcceptInviteRequest;
import com.commonground.volunteer.web.dto.AuthResponse;
import com.commonground.volunteer.web.dto.InviteRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

  private final InvitationRepository invitationRepository;
  private final UserAccountRepository userAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthService authService;

  public InvitationService(
      InvitationRepository invitationRepository,
      UserAccountRepository userAccountRepository,
      PasswordEncoder passwordEncoder,
      AuthService authService
  ) {
    this.invitationRepository = invitationRepository;
    this.userAccountRepository = userAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.authService = authService;
  }

  @Transactional
  public Map<String, Object> invite(UserPrincipal principal, InviteRequest request) {
    if (principal.role() != Role.ORGANIZER) {
      throw new ApiException("Only organizers can invite members");
    }
    if (request.role() == Role.ORGANIZER) {
      throw new ApiException("Cannot invite as organizer; use signup instead");
    }
    if (principal.organizationId() == null) {
      throw new ApiException("Organizer has no organization");
    }

    UserAccount inviter = userAccountRepository.findById(Objects.requireNonNull(principal.id()))
        .orElseThrow(() -> new ApiException("User not found"));
    Organization organization = inviter.getOrganization();
    if (organization == null) {
      throw new ApiException("Organizer has no organization");
    }

    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (userAccountRepository.existsByEmailIgnoreCase(email)) {
      throw new ApiException("A user with this email already exists");
    }

    Invitation invitation = new Invitation();
    invitation.setOrganization(organization);
    invitation.setEmail(email);
    invitation.setRole(request.role());
    invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
    invitation.setInvitedBy(inviter);
    invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    invitation = invitationRepository.save(invitation);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", invitation.getId());
    payload.put("email", invitation.getEmail());
    payload.put("role", invitation.getRole().name());
    payload.put("token", invitation.getToken());
    payload.put("expiresAt", invitation.getExpiresAt().toString());
    payload.put("invitePath", "/accept-invite?token=" + invitation.getToken());
    return payload;
  }

  @Transactional
  public AuthResponse accept(AcceptInviteRequest request) {
    Invitation invitation = invitationRepository.findByToken(request.token())
        .orElseThrow(() -> new ApiException("Invalid invitation token"));

    if (invitation.getAcceptedAt() != null) {
      throw new ApiException("Invitation already accepted");
    }
    if (invitation.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException("Invitation has expired");
    }
    if (userAccountRepository.existsByEmailIgnoreCase(invitation.getEmail())) {
      throw new ApiException("An account with this email already exists");
    }

    Organization organization = invitation.getOrganization();
    if (organization == null) {
      throw new ApiException("Invitation has no organization");
    }

    UserAccount user = new UserAccount();
    user.setEmail(invitation.getEmail());
    user.setFullName(request.fullName().trim());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(invitation.getRole());
    user.setOrganization(organization);
    user = userAccountRepository.save(user);

    invitation.setAcceptedAt(Instant.now());
    invitationRepository.save(invitation);

    return authService.toAuthResponse(user);
  }
}
