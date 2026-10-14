package com.commonground.volunteer.repository;

import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);
  Optional<UserAccount> findByGoogleSub(String googleSub);
  Optional<UserAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);
  List<UserAccount> findByOrganizationIdOrderByFullNameAsc(UUID organizationId);
  List<UserAccount> findByOrganizationIdAndRoleInAndActiveTrue(UUID organizationId, List<Role> roles);
  long countByOrganizationIdAndRoleAndActiveTrue(UUID organizationId, Role role);
}
