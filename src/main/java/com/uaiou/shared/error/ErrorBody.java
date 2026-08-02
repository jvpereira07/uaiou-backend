package com.uaiou.shared.error;

import java.util.Map;

/**
 * Corpo do erro no formato do contrato (api/README.md): {@code code} é estável e faz parte do
 * contrato do cliente; {@code rule} só aparece quando o erro decorre de regra de negócio
 * documentada.
 */
public record ErrorBody(String code, String message, String rule, Map<String, Object> details) {}
