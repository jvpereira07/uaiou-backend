package com.uaiou.presence.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /me/availability} (api/usuarios.md). */
public record UpdateAvailabilityRequest(@NotNull Boolean available) {}
