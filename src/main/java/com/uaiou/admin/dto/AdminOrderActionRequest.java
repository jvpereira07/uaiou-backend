package com.uaiou.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/orders/{id}/actions} — motivo obrigatório: vai para a auditoria (RF-07.1). */
public record AdminOrderActionRequest(
    @NotNull AdminOrderAction action, @NotBlank @Size(max = 280) String reason) {}
