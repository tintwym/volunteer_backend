package dev.tintwym.volunteer.web.controller;

import dev.tintwym.volunteer.security.UserPrincipal;
import dev.tintwym.volunteer.service.AuthService;
import dev.tintwym.volunteer.service.GoogleAuthService;
import dev.tintwym.volunteer.service.InvitationService;
import dev.tintwym.volunteer.service.PasswordResetService;
import dev.tintwym.volunteer.web.dto.AcceptInviteRequest;
import dev.tintwym.volunteer.web.dto.AccountDtos.AuthConfigResponse;
import dev.tintwym.volunteer.web.dto.AccountDtos.ChangePasswordRequest;
import dev.tintwym.volunteer.web.dto.AccountDtos.ForgotPasswordRequest;
import dev.tintwym.volunteer.web.dto.AccountDtos.GoogleLoginRequest;
import dev.tintwym.volunteer.web.dto.AccountDtos.MessageResponse;
import dev.tintwym.volunteer.web.dto.AccountDtos.ResetPasswordRequest;
import dev.tintwym.volunteer.web.dto.AccountDtos.UpdateProfileRequest;
import dev.tintwym.volunteer.web.dto.AuthResponse;
import dev.tintwym.volunteer.web.dto.LoginRequest;
import dev.tintwym.volunteer.web.dto.SignupRequest;
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
