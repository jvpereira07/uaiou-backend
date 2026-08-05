package com.uaiou.counteroffers.repository;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContraofertaRepository extends JpaRepository<Contraoferta, UUID> {

  List<Contraoferta> findByPedidoIdAndStatus(UUID pedidoId, CounterofferStatus status);

  List<Contraoferta> findByPedidoIdOrderByCriadoEmDesc(UUID pedidoId);

  boolean existsByPedidoIdAndEntregadorIdAndStatus(
      UUID pedidoId, UUID entregadorId, CounterofferStatus status);

  /** RF-22.2 — taxa de sucesso das próprias contraofertas do entregador, no período. */
  List<Contraoferta> findByEntregadorIdAndCriadoEmBetween(
      UUID entregadorId, java.time.Instant de, java.time.Instant ate);

  /** RF-22.3 — taxa de aceite de contraofertas recebidas pelo estabelecimento, no período. */
  @org.springframework.data.jpa.repository.Query(
      "select c from Contraoferta c where c.pedidoId in"
          + " (select p.id from Pedido p where p.estabelecimentoId = :estabelecimentoId)"
          + " and c.criadoEm between :de and :ate")
  List<Contraoferta> findRecebidasPeloEstabelecimento(
      @org.springframework.data.repository.query.Param("estabelecimentoId") UUID estabelecimentoId,
      @org.springframework.data.repository.query.Param("de") java.time.Instant de,
      @org.springframework.data.repository.query.Param("ate") java.time.Instant ate);
}
