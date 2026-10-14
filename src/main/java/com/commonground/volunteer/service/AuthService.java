package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Organization;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.repository.OrganizationRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.JwtService;
import com.commonground.volunteer.web.dto.AccountDtos;
import com.commonground.volunteer.web.dto.AuthResponse;
import com.commonground.volunteer.web.dto.LoginRequest;
import com.commonground.volunteer.web.dto.SignupRequest;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final int MAX_FAILED_LOGINS = 10;
  private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

  private record FailedLogins(int count, Instant windowStart) {}

  private final Map<String, FailedLogins> failedLogins = new ConcurrentHashMap<>();

  private final UserAccountRepository userAccountRepository;
  private final OrganizationRepository organizationRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserAccountRepository userAccountRepository,
      OrganizationRepository organizationRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService
  ) {
    this.userAccountRepository = userAccountRepository;
    this.organizationRepository = organizationRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthResponse signup(SignupRequest request) {
    return toAuthResponse(createOrganizer(request.fullName(), request.email(), request.password(),
        request.organizationName(), null));
  }

  /** Creates an organization and its first organizer. A null password produces an unusable random one. */
  @Transactional
  public UserAccount createOrganizer(
      String fullName, String rawEmail, String password, String organizationName, String googleSub) {
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    if (userAccountRepository.existsByEmailIgnoreCase(email)) {
      throw new ApiException("An account with this email already exists");
    }

    Organization organization = new Organization();
    organization.setName(organizationName.trim());
    organization.setSlug(uniqueSlug(organizationName));
    organization = organizationRepository.save(organization);

    UserAccount user = new UserAccount();
    user.setEmail(email);
    user.setFullName(fullName.trim());
    user.setPasswordHash(passwordEncoder.encode(password != null ? password : UUID.randomUUID() + "!" + UUID.randomUUID()));
    user.setRole(Role.ORGANIZER); // signup always defaults to organizer
    user.setOrganization(organization);
    user.setGoogleSub(googleSub);
    return userAccountRepository.save(user);
  }

  @Transactional
  public AuthResponse.UserResponse updateProfile(@NonNull UUID userId, AccountDtos.UpdateProfileRequest request) {
    UserAccount user = userAccountRepository.findById(userId)
        .orElseThrow(() -> new ApiException("User not found"));
    if (request.fullName() != null) {
      String name = request.fullName().trim();
      if (name.isEmpty()) {
        throw new ApiException("Full name cannot be empty");
      }
      user.setFullName(name);
    }
    if (request.phone() != null) {
      String phone = request.phone().trim();
      user.setPhone(phone.isEmpty() ? null : phone);
    }
    return toUserResponse(user);
  }

  /** Returns a fresh token because changing the password revokes all earlier ones. */
  @Transactional
  public AuthResponse changePassword(@NonNull UUID userId, AccountDtos.ChangePasswordRequest request) {
    UserAccount user = userAccountRepository.findById(userId)
        .orElseThrow(() -> new ApiException("User not found"));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException("Current password is incorrect");
    }
    user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    return toAuthResponse(user);
  }

  private void recordFailedLogin(String email, Instant now) {
    if (failedLogins.size() > 10_000) {
      failedLogins.values().removeIf(f -> f.windowStart().plus(LOCKOUT_WINDOW).isBefore(now));
    }
    failedLogins.compute(email, (key, current) ->
        current == null || current.windowStart().plus(LOCKOUT_WINDOW).isBefore(now)
            ? new FailedLogins(1, now)
            : new FailedLogins(current.count() + 1, current.windowStart()));
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now();
    FailedLogins failures = failedLogins.get(email);
    if (failures != null && failures.count() >= MAX_FAILED_LOGINS
        && failures.windowStart().plus(LOCKOUT_WINDOW).isAfter(now)) {
      throw new ApiException("Too many failed sign-in attempts. Try again in a few minutes.");
    }

    UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
    if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      recordFailedLogin(email, now);
      throw new ApiException("Invalid email or password");
    }
    failedLogins.remove(email);
    if (!user.isActive()) {
      throw new ApiException("Account is disabled");
    }

    return toAuthResponse(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse.UserResponse me(@NonNull UUID userId) {
    UserAccount user = userAccountRepository.findById(Objects.requireNonNull(userId))
        .orElseThrow(() -> new ApiException("User not found"));
    return toUserResponse(user);
  }

  public AuthResponse toAuthResponse(UserAccount user) {
    UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
    String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole(), orgId);
    return AuthResponse.bearer(token, toUserResponse(user));
  }

  public AuthResponse.UserResponse toUserResponse(UserAccount user) {
    UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
    String orgName = user.getOrganization() != null ? user.getOrganization().getName() : null;
    return new AuthResponse.UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getRole(),
        orgId,
        orgName,
        user.getPhone()
    );
  }

  private String uniqueSlug(String name) {
    String normalized = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD);
    StringBuilder ascii = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c <= 127) {
        ascii.append(c);
      }
    }
    String base = ascii.toString()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
    if (base.isBlank()) {
      base = "org";
    }
    String slug = base;
    int i = 1;
    while (organizationRepository.existsBySlug(slug)) {
      slug = base + "-" + i++;
    }
    return slug;
  }
}
