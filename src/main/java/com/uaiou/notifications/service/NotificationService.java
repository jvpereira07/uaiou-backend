package com.uaiou.notifications.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.entity.Dispositivo;
import com.uaiou.notifications.entity.Notificacao;
import com.uaiou.notifications.entity.PreferenciaNotificacao;
import com.uaiou.notifications.repository.DispositivoRepository;
import com.uaiou.notifications.repository.NotificacaoRepository;
import com.uaiou.notifications.repository.PreferenciaNotificacaoRepository;
import com.uaiou.shared.id.UuidV7;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * RF-08.1/RF-08.2 — a operação única de publicação: {@code publicar(usuario, tipo, payload)}.
 *
 * <p><strong>Persistir sempre, empurrar quando der.</strong> A linha em {@code notificacao} é
 * gravada incondicionalmente; o push é tentativa adicional que pode falhar sem consequência para o
 * evento. Tratar push como fonte primária é o erro que faz a contingência (T-16) falhar em
 * silêncio.
 *
 * <p>Chamada <strong>depois do commit</strong> da transação de negócio (RF-08.1): falha de push
 * nunca desfaz uma entrega, porque a notificação é efeito do fato, não parte dele. Quem garante
 * isso é o chamador, com {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificacaoRepository notificacaoRepository;
  private final DispositivoRepository dispositivoRepository;
  private final PreferenciaNotificacaoRepository preferenciaRepository;
  private final PushSender pushSender;
  private final ObjectMapper objectMapper;

  public NotificationService(
      NotificacaoRepository notificacaoRepository,
      DispositivoRepository dispositivoRepository,
      PreferenciaNotificacaoRepository preferenciaRepository,
      PushSender pushSender,
      ObjectMapper objectMapper) {
    this.notificacaoRepository = notificacaoRepository;
    this.dispositivoRepository = dispositivoRepository;
    this.preferenciaRepository = preferenciaRepository;
    this.pushSender = pushSender;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Notificacao publicar(
      UUID usuarioId,
      NotificationType tipo,
      String titulo,
      String corpo,
      Map<String, Object> payload) {
    Notificacao notificacao =
        notificacaoRepository.save(
            new Notificacao(UuidV7.next(), usuarioId, tipo, titulo, corpo, serializar(payload)));

    // RF-08.9: preferência do usuário só alcança o PUSH. A linha do inbox já foi gravada acima e
    // não é negociável — silenciar o canal não pode apagar o evento.
    if (podeEmpurrar(usuarioId, tipo)) {
      empurrar(notificacao);
    }
    return notificacao;
  }

  /** RF-08.10 — fan-out 1:N; cada destinatário recebe a própria linha de inbox. */
  @Transactional
  public void publicarParaVarios(
      List<UUID> usuarioIds,
      NotificationType tipo,
      String titulo,
      String corpo,
      Map<String, Object> payload) {
    for (UUID usuarioId : usuarioIds) {
      publicar(usuarioId, tipo, titulo, corpo, payload);
    }
  }

  /**
   * RF-08.11 — falha de push é registrada e engolida: a notificação já está no inbox, e derrubar o
   * fluxo por causa do transporte transformaria "push não chegou" em "evento não aconteceu". Retry
   * com recuo exponencial é responsabilidade do transporte real (ver {@link PushSender}).
   */
  private void empurrar(Notificacao notificacao) {
    try {
      List<Dispositivo> destinos =
          dispositivoRepository.findByUsuarioId(notificacao.getUsuarioId());
      List<String> tokensInvalidos = pushSender.send(notificacao, destinos);
      // RF-08.4: quem descobre que o aparelho sumiu é o provedor, não o cliente.
      for (String token : tokensInvalidos) {
        dispositivoRepository.findByPushToken(token).ifPresent(dispositivoRepository::delete);
      }
    } catch (RuntimeException e) {
      log.warn(
          "Falha ao empurrar push da notificação {} — inbox já persistido, evento não se perde.",
          notificacao.getId(),
          e);
    }
  }

  private boolean podeEmpurrar(UUID usuarioId, NotificationType tipo) {
    if (tipo.isMandatory()) {
      return true;
    }
    return preferenciaRepository
        .findById(usuarioId)
        .map(PreferenciaNotificacao::isPush)
        // Ausência de linha = tudo ligado (o padrão é receber).
        .orElse(true);
  }

  private String serializar(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    return objectMapper.writeValueAsString(payload);
  }
}
