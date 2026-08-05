package com.uaiou.delivery.repository;

import com.uaiou.delivery.entity.EvidenciaEntrega;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenciaEntregaRepository extends JpaRepository<EvidenciaEntrega, UUID> {

  Optional<EvidenciaEntrega> findByPedidoId(UUID pedidoId);

  /** RF-17.2 — {@code proofUploadId} já usado por outra entrega → 409, nunca reaproveitado. */
  boolean existsByUploadId(UUID uploadId);
}
