package com.uaiou.blocks.repository;

import com.uaiou.blocks.entity.Bloqueio;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueioRepository extends JpaRepository<Bloqueio, UUID> {

  /** RF-11.5 — usado no sentido "pedido → entregadores elegíveis" (fan-out de publicação). */
  @Query("select b.entregadorId from Bloqueio b where b.estabelecimentoId = :estabelecimentoId")
  List<UUID> findEntregadoresBloqueadosPor(@Param("estabelecimentoId") UUID estabelecimentoId);

  boolean existsByEstabelecimentoIdAndEntregadorId(UUID estabelecimentoId, UUID entregadorId);
}
