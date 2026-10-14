package com.commonground.volunteer.security;

import com.commonground.volunteer.config.JwtProperties;
import com.commonground.volunteer.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class JwtService {

  private final JwtProperties properties;
  private final SecretKey key;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    Assert.hasText(properties.secret(), "app.jwt.secret must be configured");
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(UUID userId, String email, Role role, UUID organizationId) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + properties.expirationMs());

    var builder = Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .claim("role", role.name())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key);

    if (organizationId != null) {
      builder.claim("organizationId", organizationId.toString());
    }

    return builder.compact();
  }

  public Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public UUID getUserId(String token) {
    return userIdFrom(parseClaims(token));
  }

  public UUID userIdFrom(Claims claims) {
    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("JWT subject is missing");
    }
    return UUID.fromString(subject);
  }
}
