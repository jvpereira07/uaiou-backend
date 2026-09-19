package com.uaiou.admin.dto;

import java.util.Map;

/**
 * {@code GET /admin/orders/overview} — contagem por status (chave = status do contrato) e, por
 * regra de timeout, quantos pedidos já passaram do prazo configurado agora.
 */
public record AdminOrdersOverview(Map<String, Long> byStatus, Map<String, Integer> overdue) {}
