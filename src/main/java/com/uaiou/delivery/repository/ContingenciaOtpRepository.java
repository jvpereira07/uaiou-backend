package com.uaiou.delivery.repository;

import com.uaiou.delivery.ContingencyChannel;
import com.uaiou.delivery.ContingencyResult;
import com.uaiou.delivery.entity.ContingenciaOtp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContingenciaOtpRepository extends JpaRepository<ContingenciaOtp, UUID> {

  List<ContingenciaOtp> findByPedidoIdOrderByCriadoEmAsc(UUID pedidoId);

  int countByPedidoIdAndCanalAndResultado(
      UUID pedidoId, ContingencyChannel canal, ContingencyResult resultado);

  /**
   * RF-16.7 — reentrância: degrau 2 "em aberto" é a última linha desse degrau cujo prazo ainda não
   * venceu e que ainda não foi resolvida (repassado/expirado). Existir uma dessas é o que diz ao
   * serviço "já acionei, devolva o estado atual em vez de acionar de novo".
   */
  Optional<ContingenciaOtp> findFirstByPedidoIdAndDegrauOrderByCriadoEmDesc(
      UUID pedidoId, int degrau);

  /** Job de expiração: degraus 2 com prazo vencido e ainda sem resultado terminal. */
  List<ContingenciaOtp> findByDegrauAndResultadoAndPrazoEmBefore(
      int degrau, ContingencyResult resultado, Instant limite);
}
