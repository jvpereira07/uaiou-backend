package com.uaiou.presence.service;

import com.uaiou.presence.config.PresenceProperties;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-10.8 — quem parou de reportar posição além do limite de frescor deixa de estar disponível. Sem
 * isso a plataforma acumula entregadores fantasmas e a cobertura parece melhor do que é.
 *
 * <p>O TTL do Redis já esconde a posição velha da listagem (critério de aceite 6) no mesmo instante
 * em que ela vence; este job é a <em>reconciliação</em> do lado durável: sincroniza o {@code
 * disponivel} do PostgreSQL — que é a fonte de verdade — e limpa o SET de disponíveis, que não tem
 * TTL por membro.
 *
 * <p>Intervalo curto de propósito: é a diferença entre "some da listagem na hora" (TTL) e "a conta
 * reflete a verdade" (banco). Um intervalo longo deixaria {@code GET /me} mostrando "disponível"
 * para quem a elegibilidade já ignora.
 */
@Component
public class PresenceExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(PresenceExpiryJob.class);

  private final EntregadorRepository entregadorRepository;
  private final PresenceCache presenceCache;
  private final PresenceProperties properties;

  public PresenceExpiryJob(
      EntregadorRepository entregadorRepository,
      PresenceCache presenceCache,
      PresenceProperties properties) {
    this.entregadorRepository = entregadorRepository;
    this.presenceCache = presenceCache;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "PT1M")
  @Transactional
  public void desligarPresencasVencidas() {
    Instant limite = Instant.now().minus(properties.freshness());
    List<Entregador> vencidos = entregadorRepository.findDisponiveisComPosicaoVencida(limite);
    for (Entregador entregador : vencidos) {
      entregador.ficarIndisponivel();
      presenceCache.markUnavailable(entregador.getUsuarioId());
    }
    if (!vencidos.isEmpty()) {
      log.info(
          "Expiração de presença: {} entregador(es) marcado(s) indisponível(is) por posição vencida.",
          vencidos.size());
    }
  }
}
