package com.uaiou.credits.repository;

import com.uaiou.credits.SubscriptionStatus;
import com.uaiou.credits.entity.Assinatura;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {

  Optional<Assinatura> findByEstabelecimentoIdAndStatus(
      UUID estabelecimentoId, SubscriptionStatus status);

  /** RF-09.4 — base do job de renovação: ciclos vencidos ainda ativos. */
  List<Assinatura> findByStatusAndProximaRenovacaoLessThanEqual(
      SubscriptionStatus status, LocalDate data);
}
