package com.uaiou.counteroffers.repository;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContraofertaRepository extends JpaRepository<Contraoferta, UUID> {

  List<Contraoferta> findByPedidoIdAndStatus(UUID pedidoId, CounterofferStatus status);
}
