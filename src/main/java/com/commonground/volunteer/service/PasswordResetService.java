package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.PasswordResetToken;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ApiException;
import com.commonground.volunteer.repository.PasswordResetTokenRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset via one-time links. There is no email provider yet, so the link is written to the
 * application log ({@code app.mail.log-reset-links}).
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
  private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserAccountRepository userAccountRepository;
  private final PasswordResetTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final String frontendUrl;
  private final boolean logResetLinks;
  private final Map<String, Instant> lastRequestByEmail = new ConcurrentHashMap<>();

  public PasswordResetService(
      UserAccountRepository userAccountRepository,
      PasswordResetTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl,
      @Value("${app.mail.log-reset-links:true}") boolean logResetLinks
  ) {
    this.userAccountRepository = userAccountRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    this.logResetLinks = logResetLinks;
  }

  /** Always succeeds silently so the response never reveals whether an email is registered. */
  @Transactional
  public void requestReset(String rawEmail) {
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now();
    Instant last = lastRequestByEmail.get(email);
    if (last != null && last.plus(REQUEST_COOLDOWN).isAfter(now)) {
      return;
    }
    if (lastRequestByEmail.size() > 10_000) {
      lastRequestByEmail.values().removeIf(t -> t.plus(REQUEST_COOLDOWN).isBefore(now));
    }
    lastRequestByEmail.put(email, now);

    UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
    if (user == null || !user.isActive()) {
      return;
    }
    tokenRepository.invalidateAllForUser(user.getId(), now);

    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    PasswordResetToken token = new PasswordResetToken();
    token.setUser(user);
    token.setTokenHash(sha256(rawToken));
    token.setExpiresAt(now.plus(TOKEN_TTL));
    tokenRepository.save(token);

    String link = frontendUrl + "/reset-password?token=" + rawToken;
    if (logResetLinks) {
      log.info("[DEV EMAIL] Password reset link for {} (valid {} min): {}", email, TOKEN_TTL.toMinutes(), link);
    }
  }

  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken.trim()))
        .orElseThrow(() -> new ApiException("This reset link is invalid or has expired"));
    Instant now = Instant.now();
    if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
      throw new ApiException("This reset link is invalid or has expired");
    }
    UserAccount user = token.getUser();
    if (!user.isActive()) {
      throw new ApiException("Account is disabled");
    }
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    tokenRepository.invalidateAllForUser(user.getId(), now);
  }

  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
