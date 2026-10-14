package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Invitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
  Optional<Invitation> findByToken(String token);
}
