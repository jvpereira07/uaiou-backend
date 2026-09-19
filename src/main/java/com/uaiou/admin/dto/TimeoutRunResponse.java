package com.uaiou.admin.dto;

import java.util.Map;

/** {@code POST /admin/timeouts/run} — pedidos afetados por regra ligada nesta execução. */
public record TimeoutRunResponse(Map<String, Integer> affected) {}
