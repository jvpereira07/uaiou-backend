package com.uaiou.blocks.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code POST /me/blocked-couriers} (api/usuarios.md). {@code reason} é livre e opcional. */
public record BlockCourierRequest(@NotNull UUID courierId, String reason) {}
