package com.commonground.volunteer.security;

import com.commonground.volunteer.domain.Role;
import com.commonground.volunteer.domain.UserAccount;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
    @NonNull UUID id,
    @NonNull String email,
    @NonNull String passwordHash,
    @NonNull String fullName,
    @NonNull Role role,
    @Nullable UUID organizationId,
    boolean active
) implements UserDetails {

  public static UserPrincipal from(UserAccount user) {
    UUID orgId = user.getOrganization() != null ? user.getOrganization().getId() : null;
    return new UserPrincipal(
        Objects.requireNonNull(user.getId(), "id"),
        Objects.requireNonNull(user.getEmail(), "email"),
        Objects.requireNonNull(user.getPasswordHash(), "passwordHash"),
        Objects.requireNonNull(user.getFullName(), "fullName"),
        Objects.requireNonNull(user.getRole(), "role"),
        orgId,
        user.isActive()
    );
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return active;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
