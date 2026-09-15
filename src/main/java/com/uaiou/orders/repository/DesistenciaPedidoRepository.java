package com.uaiou.orders.repository;

import com.uaiou.orders.entity.DesistenciaPedido;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesistenciaPedidoRepository extends JpaRepository<DesistenciaPedido, UUID> {

  /** RF-26.27 — quem desistiu não reaceita o mesmo pedido. */
  boolean existsByPedidoIdAndEntregadorId(UUID pedidoId, UUID entregadorId);

  /** RF-26.27 — o fan-out de republicação não avisa quem já desistiu do pedido. */
  List<DesistenciaPedido> findByPedidoId(UUID pedidoId);

  /** RF-26.29 — janela móvel do limite, só com as que contam (RF-26.30). */
  List<DesistenciaPedido> findByEntregadorIdAndContaPenalidadeTrueAndCriadoEmAfterOrderByCriadoEmDesc(
      UUID entregadorId, Instant desde);

  /** RF-26.31 — insumo do componente de conclusão do score. */
  List<DesistenciaPedido> findByEntregadorIdAndContaPenalidadeTrue(UUID entregadorId);
}
