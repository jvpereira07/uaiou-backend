package com.uaiou.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignPlanRequest(@NotNull UUID planId) {}
