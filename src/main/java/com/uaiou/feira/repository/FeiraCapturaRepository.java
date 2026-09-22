package com.uaiou.feira.repository;

import com.uaiou.feira.entity.FeiraCaptura;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeiraCapturaRepository extends JpaRepository<FeiraCaptura, UUID> {

  List<FeiraCaptura> findByPedidoIdOrderByCriadoEmAsc(UUID pedidoId);

  List<FeiraCaptura> findByEntregadorIdOrderByCriadoEmDesc(UUID entregadorId);

  boolean existsByPedidoIdAndEntregadorId(UUID pedidoId, UUID entregadorId);

  /** V29 — da instância de volta para a captura que a gerou. */
  Optional<FeiraCaptura> findByPedidoGeradoId(UUID pedidoGeradoId);
}
