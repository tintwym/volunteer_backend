package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.Role;
import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String tokenType,
    UserResponse user
) {
  public static AuthResponse bearer(String token, UserResponse user) {
    return new AuthResponse(token, "Bearer", user);
  }

  public record UserResponse(
      UUID id,
      String email,
      String fullName,
      Role role,
      UUID organizationId,
      String organizationName,
      String phone
  ) {}
}
