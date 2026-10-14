package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.AuthService;
import com.commonground.volunteer.service.GoogleAuthService;
import com.commonground.volunteer.service.InvitationService;
import com.commonground.volunteer.service.PasswordResetService;
import com.commonground.volunteer.web.dto.AcceptInviteRequest;
import com.commonground.volunteer.web.dto.AccountDtos.AuthConfigResponse;
import com.commonground.volunteer.web.dto.AccountDtos.ChangePasswordRequest;
import com.commonground.volunteer.web.dto.AccountDtos.ForgotPasswordRequest;
import com.commonground.volunteer.web.dto.AccountDtos.GoogleLoginRequest;
import com.commonground.volunteer.web.dto.AccountDtos.MessageResponse;
import com.commonground.volunteer.web.dto.AccountDtos.ResetPasswordRequest;
import com.commonground.volunteer.web.dto.AccountDtos.UpdateProfileRequest;
import com.commonground.volunteer.web.dto.AuthResponse;
import com.commonground.volunteer.web.dto.LoginRequest;
import com.commonground.volunteer.web.dto.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final InvitationService invitationService;
  private final PasswordResetService passwordResetService;
  private final GoogleAuthService googleAuthService;

  public AuthController(
      AuthService authService,
      InvitationService invitationService,
      PasswordResetService passwordResetService,
      GoogleAuthService googleAuthService
  ) {
    this.authService = authService;
    this.invitationService = invitationService;
    this.passwordResetService = passwordResetService;
    this.googleAuthService = googleAuthService;
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/accept-invite")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
    return invitationService.accept(request);
  }

  @GetMapping("/config")
  public AuthConfigResponse config() {
    return googleAuthService.config();
  }

  @PostMapping("/google")
  public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
    return googleAuthService.login(request);
  }

  @PostMapping("/forgot-password")
  public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordResetService.requestReset(request.email());
    return new MessageResponse("If an account exists for that email, a reset link has been sent.");
  }

  @PostMapping("/reset-password")
  public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordResetService.resetPassword(request.token(), request.password());
    return new MessageResponse("Your password has been reset. You can now sign in.");
  }

  @GetMapping("/me")
  public AuthResponse.UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
    return authService.me(principal.id());
  }

  @PatchMapping("/me")
  public AuthResponse.UserResponse updateMe(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request
  ) {
    return authService.updateProfile(principal.id(), request);
  }

  @PostMapping("/change-password")
  public AuthResponse changePassword(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ChangePasswordRequest request
  ) {
    return authService.changePassword(principal.id(), request);
  }
}
