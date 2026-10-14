package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Message;
import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.ForbiddenException;
import com.commonground.volunteer.repository.MessageRepository;
import com.commonground.volunteer.repository.UserAccountRepository;
import com.commonground.volunteer.security.AccessGuard;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.WorkDtos.MessageRequest;
import com.commonground.volunteer.web.dto.WorkDtos.MessageResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization-wide channels. GENERAL is open to everyone, LEADERS to organizers and leaders, and
 * ANNOUNCEMENTS can be read by everyone but only posted by staff.
 */
@Service
public class MessageService {

  private final MessageRepository messageRepository;
  private final UserAccountRepository userAccountRepository;
  private final NotificationService notificationService;
  private final AccessGuard access;

  public MessageService(
      MessageRepository messageRepository,
      UserAccountRepository userAccountRepository,
      NotificationService notificationService,
      AccessGuard access
  ) {
    this.messageRepository = messageRepository;
    this.userAccountRepository = userAccountRepository;
    this.notificationService = notificationService;
    this.access = access;
  }

  /** Most recent messages, returned oldest-first for chat display. */
  @Transactional(readOnly = true)
  public List<MessageResponse> list(UserPrincipal principal, Message.Channel channel, int limit) {
    UUID orgId = access.orgId(principal);
    if (channel == Message.Channel.LEADERS) {
      access.requireStaff(principal);
    }
    int size = Math.max(1, Math.min(limit, 200));
    List<MessageResponse> items = new ArrayList<>(messageRepository
        .findByOrganizationIdAndChannelOrderByCreatedAtDesc(orgId, channel, PageRequest.of(0, size))
        .stream().map(MessageService::toResponse).toList());
    Collections.reverse(items);
    return items;
  }

  @Transactional
  public MessageResponse post(UserPrincipal principal, Message.Channel channel, MessageRequest request) {
    UUID orgId = access.orgId(principal);
    if (channel != Message.Channel.GENERAL && !access.isStaff(principal)) {
      throw new ForbiddenException("Only organizers and leaders can post here");
    }
    UserAccount sender = access.currentUser(principal);
    boolean urgent = request.urgent() && access.isStaff(principal);
    Message message = new Message();
    message.setOrganization(sender.getOrganization());
    message.setChannel(channel);
    message.setSender(sender);
    message.setContent(request.content().trim());
    message.setUrgent(urgent);
    message = messageRepository.save(message);

    if (channel == Message.Channel.ANNOUNCEMENTS || urgent) {
      List<Role> audience = channel == Message.Channel.LEADERS
          ? List.of(Role.ORGANIZER, Role.VOLUNTEER_LEADER)
          : List.of(Role.ORGANIZER, Role.VOLUNTEER_LEADER, Role.VOLUNTEER);
      List<UserAccount> recipients = userAccountRepository
          .findByOrganizationIdAndRoleInAndActiveTrue(orgId, audience)
          .stream().filter(u -> !u.getId().equals(principal.id())).toList();
      String preview = message.getContent().length() > 140
          ? message.getContent().substring(0, 137) + "..."
          : message.getContent();
      notificationService.notifyAll(recipients, "MESSAGE",
          (urgent ? "Urgent: " : "Announcement from ") + sender.getFullName(), preview, "/messages");
    }
    return toResponse(message);
  }

  private static MessageResponse toResponse(Message m) {
    UserAccount sender = m.getSender();
    return new MessageResponse(
        m.getId(), m.getChannel(),
        sender != null ? sender.getId() : null,
        sender != null ? sender.getFullName() : "Former member",
        sender != null ? sender.getRole() : null,
        m.getContent(), m.isUrgent(), m.getCreatedAt());
  }
}
