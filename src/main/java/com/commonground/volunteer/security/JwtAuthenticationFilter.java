package com.commonground.volunteer.security;

import com.commonground.volunteer.repository.UserAccountRepository;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private final JwtService jwtService;
  private final UserAccountRepository userAccountRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository userAccountRepository) {
    this.jwtService = jwtService;
    this.userAccountRepository = userAccountRepository;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (header == null || !header.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        Claims claims = jwtService.parseClaims(token);
        UUID userId = Objects.requireNonNull(jwtService.userIdFrom(claims), "userId");
        Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.EPOCH;
        userAccountRepository.findById(userId)
            .filter(user -> user.isActive())
            .filter(user -> user.getPasswordChangedAt() == null
                || !issuedAt.isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS)))
            .ifPresent(user -> {
          UserPrincipal principal = UserPrincipal.from(user);
          UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
              principal,
              null,
              principal.getAuthorities()
          );
          auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(auth);
        });
      }
    } catch (Exception ex) {
      log.debug("JWT authentication failed: {}", ex.getMessage());
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }
}
