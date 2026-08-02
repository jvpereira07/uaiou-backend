package com.uaiou.auth.service;

/**
 * Abstração de envio (RF-03.12). Um provedor real ainda não foi escolhido — enquanto isso, a única
 * implementação é {@link LoggingEmailSender}, suficiente para desenvolvimento.
 */
public interface EmailSender {

  void sendPasswordReset(String toEmail, String resetLink);
}
