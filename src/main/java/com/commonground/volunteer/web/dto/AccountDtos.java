package com.commonground.volunteer.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

  private AccountDtos() {}

  public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}

  public record ResetPasswordRequest(
      @NotBlank @Size(max = 200) String token,
      @NotBlank @Size(min = 8, max = 100) String password
  ) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 8, max = 100) String newPassword
  ) {}

  public record UpdateProfileRequest(
      @Size(min = 1, max = 200) String fullName,
      @Size(max = 50) String phone
  ) {}

  /** {@code organizationName} is only used when the Google account has no CommonGround account yet. */
  public record GoogleLoginRequest(
      @NotBlank @Size(max = 5000) String idToken,
      @Size(max = 200) String organizationName
  ) {}

  public record AuthConfigResponse(boolean googleEnabled, String googleWebClientId) {}

  public record MessageResponse(String message) {}
}
