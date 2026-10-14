package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.web.dto.AccountDtos.AuthConfigResponse;
import com.commonground.volunteer.web.dto.AccountDtos.GoogleLoginRequest;
import com.commonground.volunteer.web.dto.AuthResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Sign-In. Clients obtain a Google ID token (web: Google Identity Services, Android: Credential
 * Manager) and exchange it here for a CommonGround JWT. Disabled until {@code app.google.client-ids} is set.
 */
@Service
public class GoogleAuthService {

  private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
  private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

  private final UserAccountRepository userAccountRepository;
  private final AuthService authService;
  private final List<String> allowedClientIds;
  private final String webClientId;
  private final RestClient restClient;

  public GoogleAuthService(
      UserAccountRepository userAccountRepository,
      AuthService authService,
      @Value("${app.google.client-ids:}") String clientIds,
      @Value("${app.google.web-client-id:}") String webClientId,
      @Value("${app.google.tokeninfo-url:https://oauth2.googleapis.com/tokeninfo}") String tokenInfoUrl
  ) {
    this.userAccountRepository = userAccountRepository;
    this.authService = authService;
    this.allowedClientIds = Arrays.stream(clientIds.split(","))
        .map(String::trim).filter(s -> !s.isEmpty()).toList();
    this.webClientId = webClientId.isBlank() && !allowedClientIds.isEmpty() ? allowedClientIds.get(0) : webClientId;
    this.restClient = RestClient.builder().baseUrl(tokenInfoUrl).build();
  }

  public AuthConfigResponse config() {
    boolean enabled = !allowedClientIds.isEmpty();
    return new AuthConfigResponse(enabled, enabled ? webClientId : null);
  }

  @Transactional
  public AuthResponse login(GoogleLoginRequest request) {
    if (allowedClientIds.isEmpty()) {
      throw new ApiException("Google sign-in is not configured on this server yet");
    }
    TokenInfo info = verify(request.idToken());
    String email = info.email().trim().toLowerCase(Locale.ROOT);

    UserAccount user = userAccountRepository.findByGoogleSub(info.sub())
        .or(() -> userAccountRepository.findByEmailIgnoreCase(email))
        .orElse(null);

    if (user == null) {
      String orgName = request.organizationName() == null ? "" : request.organizationName().trim();
      if (orgName.isEmpty()) {
        throw new ApiException("No CommonGround account uses this Google email. Ask your organizer for an "
            + "invitation, or sign up to create a new organization.");
      }
      String name = info.name() != null && !info.name().isBlank() ? info.name() : email;
      user = authService.createOrganizer(name, email, null, orgName, info.sub());
    } else {
      if (!user.isActive()) {
        throw new ApiException("Account is disabled");
      }
      if (user.getGoogleSub() == null) {
        user.setGoogleSub(info.sub());
      } else if (!user.getGoogleSub().equals(info.sub())) {
        throw new ApiException("This account is linked to a different Google account");
      }
    }
    return authService.toAuthResponse(user);
  }

  private TokenInfo verify(String idToken) {
    TokenInfo info;
    try {
      info = restClient.get()
          .uri(uri -> uri.queryParam("id_token", idToken).build())
          .retrieve()
          .body(TokenInfo.class);
    } catch (RestClientException ex) {
      log.debug("Google token verification failed: {}", ex.getMessage());
      throw new ApiException("Google sign-in failed. Please try again.");
    }
    if (info == null
        || info.sub() == null
        || info.email() == null
        || !allowedClientIds.contains(info.aud())
        || !ISSUERS.contains(info.iss())
        || !"true".equalsIgnoreCase(info.emailVerified())
        || info.exp() == null
        || Instant.ofEpochSecond(info.exp()).isBefore(Instant.now())) {
      throw new ApiException("Google sign-in failed. Please try again.");
    }
    return info;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenInfo(
      String sub,
      String aud,
      String iss,
      String email,
      @JsonProperty("email_verified") String emailVerified,
      String name,
      Long exp
  ) {}
}
