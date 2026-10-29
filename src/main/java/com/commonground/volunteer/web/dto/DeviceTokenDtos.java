package com.commonground.volunteer.web.dto;

import java.util.UUID;

public final class DeviceTokenDtos {
  private DeviceTokenDtos() {}

  public record DeviceTokenRequest(String platform, String token) {}

  public record DeviceTokenResponse(UUID id, String platform, String updatedAt) {}
}
