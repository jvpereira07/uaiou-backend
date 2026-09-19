package com.uaiou.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /admin/timeouts/{rule}} — limites iguais ao CHECK de V26 (1 min a 30 dias). */
public record UpdateTimeoutRequest(
    @NotNull @Min(1) @Max(43200) Integer minutes, @NotNull Boolean active) {}
