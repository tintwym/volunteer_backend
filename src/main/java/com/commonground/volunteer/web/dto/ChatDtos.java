package com.commonground.volunteer.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ChatDtos {

  private ChatDtos() {}

  public record ChatMessage(
      @NotBlank @Size(max = 32) String role,
      @NotBlank @Size(max = 4000) String content
  ) {}

  public record ChatRequest(
      @NotBlank @Size(max = 4000) String message,
      @Valid @Size(max = 24) List<ChatMessage> history
  ) {}

  public record ChatResponse(String reply) {}
}
