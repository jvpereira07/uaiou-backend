package com.uaiou.delivery.service;

import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.OtpRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-15.8 — a tentativa errada precisa persistir mesmo que o restante da chamada falhe por outro
 * motivo (senão o contador de força bruta reseta sozinho a cada erro). {@code REQUIRES_NEW}: commit
 * imediato, isolado da transação de negócio que está decidindo se aceita ou rejeita a finalização.
 */
@Component
public class DeliveryAttemptRecorder {

  private final OtpRepository otpRepository;
  private final DeliveryProperties properties;

  public DeliveryAttemptRecorder(OtpRepository otpRepository, DeliveryProperties properties) {
    this.otpRepository = otpRepository;
    this.properties = properties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Otp registrarErro(java.util.UUID otpId) {
    Otp otp = otpRepository.findById(otpId).orElseThrow();
    int tentativas = otp.registrarTentativaErrada();
    if (tentativas >= properties.maxAttempts()) {
      otp.bloquear();
    }
    return otpRepository.save(otp);
  }
}
