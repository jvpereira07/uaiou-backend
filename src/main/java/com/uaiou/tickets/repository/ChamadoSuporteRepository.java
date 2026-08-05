package com.uaiou.tickets.repository;

import com.uaiou.tickets.TicketStatus;
import com.uaiou.tickets.entity.ChamadoSuporte;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChamadoSuporteRepository
    extends JpaRepository<ChamadoSuporte, UUID>, JpaSpecificationExecutor<ChamadoSuporte> {

  Page<ChamadoSuporte> findByAutorIdOrderByCriadoEmDesc(UUID autorId, Pageable pageable);

  Page<ChamadoSuporte> findByStatusOrderByCriadoEmAsc(TicketStatus status, Pageable pageable);
}
