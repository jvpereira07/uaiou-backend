package com.uaiou.blocks.repository;

import com.uaiou.blocks.entity.Bloqueio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueioRepository extends JpaRepository<Bloqueio, UUID> {

  /** RF-11.5 — usado no sentido "pedido → entregadores elegíveis" (fan-out de publicação). */
  @Query("select b.entregadorId from Bloqueio b where b.estabelecimentoId = :estabelecimentoId")
  List<UUID> findEntregadoresBloqueadosPor(@Param("estabelecimentoId") UUID estabelecimentoId);

  boolean existsByEstabelecimentoIdAndEntregadorId(UUID estabelecimentoId, UUID entregadorId);

  /** RF-12.1 — lista do estabelecimento, mais recente primeiro. */
  List<Bloqueio> findByEstabelecimentoIdOrderByCriadoEmDesc(UUID estabelecimentoId);

  Optional<Bloqueio> findByEstabelecimentoIdAndEntregadorId(
      UUID estabelecimentoId, UUID entregadorId);

  /**
   * RF-12.7 — sinal agregado de moderação: quantos estabelecimentos DISTINTOS bloquearam este
   * entregador. Um bloqueio é opinião; vários independentes são evidência. A UNIQUE por par já
   * garante que o mesmo estabelecimento não conta duas vezes, mas o {@code distinct} deixa a
   * intenção explícita em vez de depender da constraint.
   */
  @Query(
      "select count(distinct b.estabelecimentoId) from Bloqueio b where b.entregadorId = :entregadorId")
  long contarEstabelecimentosDistintosQueBloquearam(@Param("entregadorId") UUID entregadorId);
}
