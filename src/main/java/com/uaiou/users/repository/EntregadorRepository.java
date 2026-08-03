package com.uaiou.users.repository;

import com.uaiou.users.entity.Entregador;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntregadorRepository extends JpaRepository<Entregador, UUID> {

  boolean existsByCpf(String cpf);

  /**
   * RF-10.6/RF-10.7 — fonte de verdade da elegibilidade por presença: disponível E com posição
   * dentro do limite de frescor. É para cá que {@code CourierPresenceService} degrada quando o
   * Redis cai (critério de aceite 7), e é a mesma pergunta que o cache responde no caminho feliz.
   */
  @Query(
      "select e from Entregador e where e.disponivel = true and e.localizacaoEm > :desde"
          + " order by e.localizacaoEm desc")
  List<Entregador> findDisponiveisComPosicaoDesde(@Param("desde") Instant desde);

  /**
   * RF-10.8 — o inverso: quem continua marcado como disponível mas parou de reportar posição.
   * Inclui quem nunca reportou ({@code localizacaoEm is null}), que só existe se a linha foi
   * manipulada fora do fluxo normal — ficar disponível exige posição recente (RF-10.2).
   */
  @Query(
      "select e from Entregador e where e.disponivel = true"
          + " and (e.localizacaoEm is null or e.localizacaoEm <= :desde)")
  List<Entregador> findDisponiveisComPosicaoVencida(@Param("desde") Instant desde);
}
