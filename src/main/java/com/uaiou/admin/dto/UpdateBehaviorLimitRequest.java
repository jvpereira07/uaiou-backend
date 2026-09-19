package com.uaiou.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /admin/limits/{rule}} — limites iguais aos CHECKs de V27. */
public record UpdateBehaviorLimitRequest(
    @NotNull @Min(1) @Max(1000) Integer max,
    @NotNull @Min(1) @Max(43200) Integer windowMinutes,
    @NotNull @Min(1) @Max(43200) Integer blockMinutes,
    @NotNull Boolean active) {}
