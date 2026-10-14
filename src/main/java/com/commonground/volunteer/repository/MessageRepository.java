package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
  List<Message> findByOrganizationIdAndChannelOrderByCreatedAtDesc(
      UUID organizationId, Message.Channel channel, Pageable pageable);
}
