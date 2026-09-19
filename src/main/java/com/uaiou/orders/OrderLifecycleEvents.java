package com.uaiou.orders;

import com.uaiou.shared.money.Money;
import java.util.List;
import java.util.UUID;

/**
 * T-26 — eventos pós-commit de coleta, cancelamento e desistência. Agrupados porque nascem da mesma
 * máquina de estados e têm um único consumidor de notificação ({@code OrderLifecycleFanout}).
 */
public final class OrderLifecycleEvents {

  private OrderLifecycleEvents() {}

  /**
   * RF-26.6/RF-26.10 — entregador no raio da loja. {@code reminder} distingue o reaviso pedido pelo
   * entregador do aviso original.
   */
  public record CourierArrived(UUID pedidoId, UUID entregadorId, boolean reminder) {}

  /** RF-26.11 — estabelecimento confirmou a coleta. */
  public record PickedUp(UUID pedidoId, UUID entregadorId) {}

  /**
   * RF-26.19 — cancelamento pelo estabelecimento. O entregador e os proponentes vêm no evento
   * porque, depois do commit, o pedido cancelado já não aponta para quem estava envolvido.
   */
  public record Cancelled(
      UUID pedidoId,
      UUID entregadorId,
      CancellationReason reason,
      Money fee,
      List<UUID> proponentesInvalidados) {}

  /** RF-26.28 — desistência do entregador; o pedido já voltou a "publicado". */
  public record CourierWithdrew(UUID pedidoId, UUID entregadorId, WithdrawalReason reason) {}

  /** Quem, fora das partes, mexeu no pedido. */
  public enum InterventionOrigin {
    ADMIN,
    TIMEOUT
  }

  /** O que a intervenção fez com o pedido. */
  public enum InterventionOutcome {
    CANCELLED,
    RETURNED_TO_SHOWCASE
  }

  /**
   * Cancelamento ou devolução à vitrine feitos pela plataforma (admin ou timeout), não pelas
   * partes. Estabelecimento e entregador vêm no evento pelo mesmo motivo de {@link Cancelled}.
   */
  public record PlatformIntervention(
      UUID pedidoId,
      UUID estabelecimentoId,
      UUID entregadorId,
      InterventionOrigin origin,
      InterventionOutcome outcome,
      List<UUID> proponentesInvalidados) {}
}
