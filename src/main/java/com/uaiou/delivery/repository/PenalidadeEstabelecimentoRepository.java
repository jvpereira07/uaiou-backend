package com.uaiou.delivery.repository;

import com.uaiou.delivery.entity.PenalidadeEstabelecimento;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PenalidadeEstabelecimentoRepository
    extends JpaRepository<PenalidadeEstabelecimento, UUID> {

  List<PenalidadeEstabelecimento> findByEstabelecimentoIdOrderByCriadoEmDesc(
      UUID estabelecimentoId);

  int countByEstabelecimentoId(UUID estabelecimentoId);

  int countByEstabelecimentoIdAndCriadoEmBetween(
      UUID estabelecimentoId, java.time.Instant de, java.time.Instant ate);
}
