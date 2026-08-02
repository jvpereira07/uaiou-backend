package com.uaiou.auth.dto;

import com.uaiou.users.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotNull Role role,
    @NotBlank @Size(min = 3, max = 60) String login,
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 8, max = 255) String password,
    @NotBlank @Size(max = 120) String displayName,
    @NotNull RegisterProfile profile) {}
