package com.uaiou.notifications.service;

import com.uaiou.notifications.entity.Dispositivo;
import com.uaiou.notifications.entity.Notificacao;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão enquanto não há credencial de provedor. Registra o que <em>seria</em>
 * enviado e não rejeita token nenhum.
 *
 * <p>Quando entrar um {@code FcmPushSender}, basta anotá-lo com {@code @Primary} — nenhum chamador
 * muda, porque ninguém depende da implementação, só de {@link PushSender}.
 *
 * <p>Deliberadamente SEM {@code @ConditionalOnMissingBean}: essa anotação só é avaliada em métodos
 * {@code @Bean} de autoconfiguração, não em classe varrida por component scan — usá-la aqui faria a
 * aplicação subir sem nenhum {@code PushSender} registrado (verificado empiricamente: o contexto
 * inteiro deixou de subir).
 *
 * <p>Isto não enfraquece T-08: RF-08.2 é explícito que a linha em {@code notificacao} é a fonte de
 * verdade e o push é tentativa adicional. O inbox funciona de verdade; o que falta é o transporte.
 */
@Component
public class LoggingPushSender implements PushSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

  @Override
  public List<String> send(Notificacao notificacao, List<Dispositivo> destinos) {
    if (destinos.isEmpty()) {
      return List.of();
    }
    log.info(
        "push \"{}\" ({}) para o usuário {} em {} dispositivo(s) — transporte real depende de"
            + " credencial de provedor (FCM/APNs), ainda não configurada.",
        notificacao.getTipo(),
        notificacao.getPrioridade().toJson(),
        notificacao.getUsuarioId(),
        destinos.size());
    return List.of();
  }
}
