package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Task;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {
  Optional<Task> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Task> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<Task> findByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId);

  long countByOrganizationIdAndStatusIn(UUID organizationId, List<Task.Status> statuses);
}
