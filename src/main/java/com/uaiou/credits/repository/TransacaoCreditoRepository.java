package com.uaiou.credits.repository;

import com.uaiou.credits.CreditTransactionType;
import com.uaiou.credits.entity.TransacaoCredito;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransacaoCreditoRepository extends JpaRepository<TransacaoCredito, UUID> {

  /** RF-09.9 — extrato paginado, mais recente primeiro. */
  Page<TransacaoCredito> findByEstabelecimentoIdOrderByCriadoEmDesc(
      UUID estabelecimentoId, Pageable pageable);

  /**
   * RF-09.8 — {@code consumedThisCycle}: soma (em módulo) do consumo desde o início do ciclo
   * vigente. Quantidade de consumo é sempre negativa no lançamento, daí o {@code -t.quantidade}.
   */
  @Query(
      "select coalesce(sum(-t.quantidade), 0) from TransacaoCredito t "
          + "where t.estabelecimentoId = :estabelecimentoId and t.tipo = :tipo and t.criadoEm >= :desde")
  int somarQuantidadeDesde(
      @Param("estabelecimentoId") UUID estabelecimentoId,
      @Param("tipo") CreditTransactionType tipo,
      @Param("desde") Instant desde);
}
