package com.uaiou.delivery.repository;

import com.uaiou.delivery.LedgerStatus;
import com.uaiou.delivery.entity.LancamentoFrete;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LancamentoFreteRepository extends JpaRepository<LancamentoFrete, UUID> {

  boolean existsByPedidoId(UUID pedidoId);

  java.util.Optional<LancamentoFrete> findByPedidoId(UUID pedidoId);

  Page<LancamentoFrete> findByEntregadorIdOrderByCriadoEmDesc(UUID entregadorId, Pageable pageable);

  List<LancamentoFrete> findByEntregadorId(UUID entregadorId);

  List<LancamentoFrete> findByEstabelecimentoIdOrderByCriadoEmDesc(UUID estabelecimentoId);

  List<LancamentoFrete> findByEntregadorIdAndStatus(UUID entregadorId, LedgerStatus status);

  /** RF-22.2/RF-22.3 — recorte por período para estatísticas. */
  List<LancamentoFrete> findByEntregadorIdAndCriadoEmBetween(
      UUID entregadorId, java.time.Instant de, java.time.Instant ate);

  List<LancamentoFrete> findByEstabelecimentoIdAndCriadoEmBetween(
      UUID estabelecimentoId, java.time.Instant de, java.time.Instant ate);
}
