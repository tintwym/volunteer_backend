package dev.tintwym.volunteer.web.controller;

import dev.tintwym.volunteer.security.UserPrincipal;
import dev.tintwym.volunteer.service.ChatService;
import dev.tintwym.volunteer.web.dto.ChatDtos.ChatRequest;
import dev.tintwym.volunteer.web.dto.ChatDtos.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private final ChatService chatService;

  public ChatController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping
  public ChatResponse chat(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ChatRequest request
  ) {
    return chatService.chat(principal, request);
  }
}
