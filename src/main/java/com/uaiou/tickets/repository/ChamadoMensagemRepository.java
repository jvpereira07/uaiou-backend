package com.uaiou.tickets.repository;

import com.uaiou.tickets.entity.ChamadoMensagem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChamadoMensagemRepository extends JpaRepository<ChamadoMensagem, UUID> {

  List<ChamadoMensagem> findByChamadoIdOrderByCriadoEmAsc(UUID chamadoId);
}
