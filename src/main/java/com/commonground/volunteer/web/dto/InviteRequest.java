package com.commonground.volunteer.web.dto;

import com.commonground.volunteer.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteRequest(
    @NotBlank @Email String email,
    @NotNull Role role
) {}
