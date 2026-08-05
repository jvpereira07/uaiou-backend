package com.uaiou.reviews.repository;

import com.uaiou.reviews.entity.Avaliacao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

  boolean existsByPedidoIdAndAutorId(UUID pedidoId, UUID autorId);

  List<Avaliacao> findByPedidoId(UUID pedidoId);

  Optional<Avaliacao> findByPedidoIdAndAutorId(UUID pedidoId, UUID autorId);

  List<Avaliacao> findByAlvoId(UUID alvoId);
}
