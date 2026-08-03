package com.uaiou.orders;

import java.util.List;
import java.util.UUID;

/**
 * RF-13.6 — efeitos pós-commit do aceite. Carrega os autores das contraofertas invalidadas porque,
 * depois do commit, a informação "quem tinha proposta pendente" já não existe mais no estado atual:
 * as linhas foram para {@code invalidada} junto.
 */
public record OrderAssignedEvent(
    UUID pedidoId, UUID entregadorId, List<UUID> proponentesInvalidados) {}
