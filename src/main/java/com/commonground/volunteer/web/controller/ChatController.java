package com.commonground.volunteer.web.controller;

import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.service.ChatService;
import com.commonground.volunteer.web.dto.ChatDtos.ChatRequest;
import com.commonground.volunteer.web.dto.ChatDtos.ChatResponse;
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
