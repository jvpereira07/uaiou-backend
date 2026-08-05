package com.uaiou.orders;

import com.uaiou.counteroffers.CounterofferStatus;
import java.util.List;
import java.util.UUID;

/**
 * RF-14.8 — o resultado sempre volta ao entregador, nos três desfechos possíveis: aceite, recusa e
 * invalidação. {@code outcome} é sempre {@link CounterofferStatus#ACCEPTED} ou {@link
 * CounterofferStatus#REJECTED} — a decisão que efetivamente aconteceu; os proponentes preteridos
 * por ela (sempre vazio na recusa, já que recusar não invalida ninguém além da própria) são
 * carregados à parte porque, depois do commit, a lista de "quem tinha proposta pendente" já não
 * existe mais no estado atual.
 */
public record CounterofferDecidedEvent(
    UUID pedidoId,
    UUID contraofertaId,
    UUID entregadorId,
    CounterofferStatus outcome,
    List<UUID> proponentesInvalidados) {}
