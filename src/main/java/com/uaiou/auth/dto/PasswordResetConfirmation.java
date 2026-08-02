package com.uaiou.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmation(@NotBlank @Size(min = 8, max = 255) String password) {}
