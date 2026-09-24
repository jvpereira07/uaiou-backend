package com.uaiou.feira.service;

import com.uaiou.feira.FeiraBonusConquistadoEvent;
import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** {@code bonus.granted} ao jogador que atingiu a meta, pós-commit. */
@Component
public class FeiraBonusFanout {

  private final NotificationService notificationService;

  public FeiraBonusFanout(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoConquistar(FeiraBonusConquistadoEvent evento) {
    notificationService.publicar(
        evento.jogadorId(),
        NotificationType.BONUS_GRANTED,
        "Bônus conquistado: " + evento.titulo(),
        "Você completou "
            + evento.meta()
            + (evento.meta() == 1 ? " entrega" : " entregas")
            + " e ganhou "
            + evento.recompensa()
            + ". Retire no estande do UaiOu.",
        Map.of(
            "bonusId", evento.bonusId().toString(),
            "conquistaId", evento.conquistaId().toString()));
  }
}
