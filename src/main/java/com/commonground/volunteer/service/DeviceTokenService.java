package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.DeviceToken;
import com.commonground.volunteer.domain.DeviceToken.Platform;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.repository.DeviceTokenRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.DeviceTokenDtos.DeviceTokenRequest;
import com.commonground.volunteer.web.dto.DeviceTokenDtos.DeviceTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final UserAccountRepository userAccountRepository;

  public DeviceTokenService(
      DeviceTokenRepository deviceTokenRepository,
      UserAccountRepository userAccountRepository
  ) {
    this.deviceTokenRepository = deviceTokenRepository;
    this.userAccountRepository = userAccountRepository;
  }

  @Transactional
  public DeviceTokenResponse upsert(UserPrincipal principal, DeviceTokenRequest request) {
    if (request.token() == null || request.token().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device token is required.");
    }
    Platform platform;
    try {
      platform = Platform.valueOf(request.platform().trim().toUpperCase());
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform must be ANDROID or IOS.");
    }

    UserAccount user = userAccountRepository.findById(principal.id())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

    String tokenValue = request.token().trim();
    DeviceToken row = deviceTokenRepository.findByToken(tokenValue).orElseGet(DeviceToken::new);
    row.setUser(user);
    row.setPlatform(platform);
    row.setToken(tokenValue);
    DeviceToken saved = deviceTokenRepository.save(row);
    return new DeviceTokenResponse(saved.getId(), saved.getPlatform().name(), saved.getUpdatedAt().toString());
  }

  @Transactional
  public void remove(UserPrincipal principal, String token) {
    if (token == null || token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device token is required.");
    }
    deviceTokenRepository.deleteByUserIdAndToken(principal.id(), token.trim());
  }
}
