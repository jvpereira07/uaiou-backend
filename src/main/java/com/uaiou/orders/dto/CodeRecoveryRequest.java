package com.uaiou.orders.dto;

/** {@code POST /orders/{id}/delivery/code-recoveries} (RF-16.1). {@code reason} é informativo. */
public record CodeRecoveryRequest(String reason) {}
