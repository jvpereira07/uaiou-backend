package com.uaiou.orders.timeout;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OcorrenciaTimeoutRepository extends JpaRepository<OcorrenciaTimeout, UUID> {

  List<OcorrenciaTimeout> findByPedidoIdOrderByCriadoEmAsc(UUID pedidoId);

  Page<OcorrenciaTimeout> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
