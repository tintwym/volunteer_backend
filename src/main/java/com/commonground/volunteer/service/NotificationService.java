package com.commonground.volunteer.service;

import com.commonground.volunteer.domain.Notification;
import com.commonground.volunteer.domain.UserAccount;
import com.commonground.volunteer.exception.NotFoundException;
import com.commonground.volunteer.repository.NotificationRepository;
import com.commonground.volunteer.security.UserPrincipal;
import com.commonground.volunteer.web.dto.WorkDtos.NotificationList;
import com.commonground.volunteer.web.dto.WorkDtos.NotificationResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional
  public void notify(UserAccount user, String type, String title, String body, String link) {
    Notification n = new Notification();
    n.setUser(user);
    n.setType(type);
    n.setTitle(title);
    n.setBody(body);
    n.setLink(link);
    notificationRepository.save(n);
  }

  @Transactional
  public void notifyAll(Collection<UserAccount> users, String type, String title, String body, String link) {
    for (UserAccount user : users) {
      notify(user, type, title, body, link);
    }
  }

  @Transactional(readOnly = true)
  public NotificationList list(UserPrincipal principal, int limit) {
    int size = Math.max(1, Math.min(limit, 100));
    var items = notificationRepository
        .findByUserIdOrderByCreatedAtDesc(principal.id(), PageRequest.of(0, size))
        .stream()
        .map(NotificationService::toResponse)
        .toList();
    return new NotificationList(notificationRepository.countByUserIdAndReadAtIsNull(principal.id()), items);
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return notificationRepository.countByUserIdAndReadAtIsNull(userId);
  }

  @Transactional
  public NotificationResponse markRead(UserPrincipal principal, UUID id) {
    Notification n = notificationRepository.findByIdAndUserId(id, principal.id())
        .orElseThrow(() -> new NotFoundException("Notification not found"));
    if (n.getReadAt() == null) {
      n.setReadAt(Instant.now());
    }
    return toResponse(n);
  }

  @Transactional
  public void markAllRead(UserPrincipal principal) {
    notificationRepository.markAllRead(principal.id(), Instant.now());
  }

  private static NotificationResponse toResponse(Notification n) {
    return new NotificationResponse(
        n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null, n.getCreatedAt());
  }
}
