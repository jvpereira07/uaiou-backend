package com.uaiou.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub de desenvolvimento (RF-03.12) — só registra o link no log, para o desenvolvedor completar o
 * fluxo manualmente. É a ÚNICA implementação hoje porque nenhum provedor real foi escolhido ainda
 * (risco registrado em T-03); trocar por um provedor de verdade antes de qualquer tráfego de
 * produção.
 */
@Component
public class LoggingEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

  @Override
  public void sendPasswordReset(String toEmail, String resetLink) {
    log.info("[dev-stub] E-mail de redefinição de senha para {}: {}", toEmail, resetLink);
  }
}
