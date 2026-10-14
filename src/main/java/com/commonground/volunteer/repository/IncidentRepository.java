package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Incident;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
  Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Incident> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<Incident> findByReportedByIdOrderByCreatedAtDesc(UUID reporterId);

  long countByOrganizationIdAndStatus(UUID organizationId, Incident.Status status);
}
