package com.uaiou.notifications;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * RF-08.6 — catálogo de eventos: <strong>tipo novo entra aqui, não como string solta no
 * serviço</strong>. Cada entrada carrega o que o resto do sistema precisa saber sobre o evento:
 * como se chama no contrato, se é urgente (RF-08.8) e se pode ser silenciado (RF-08.9).
 *
 * <p>{@code mandatory} é propriedade do EVENTO, não do usuário: contingência de código, sanção e
 * disputa são sempre entregues. Um estabelecimento que silencia a contingência trava um entregador
 * na rua e é penalizado por algo que ele mesmo desligou — por isso a decisão não é dele.
 *
 * <p>Os tipos de T-14/T-15/T-16/T-17/T-19/T-21 já constam porque o catálogo é do contrato inteiro
 * (notificacoes.md); quem os emite são as tasks que ainda não existem.
 */
public enum NotificationType {
  ORDER_PUBLISHED("order.published", NotificationPriority.NORMAL, false),
  ORDER_ASSIGNED("order.assigned", NotificationPriority.NORMAL, false),
  COUNTEROFFER_RECEIVED("counteroffer.received", NotificationPriority.NORMAL, false),
  COUNTEROFFER_DECIDED("counteroffer.decided", NotificationPriority.NORMAL, false),
  /** RN-09: tem prazo correndo — urgente e não silenciável. */
  DELIVERY_CODE_CONTINGENCY("delivery.code_contingency", NotificationPriority.URGENT, true),
  DELIVERY_COMPLETED("delivery.completed", NotificationPriority.NORMAL, false),
  DELIVERY_CONTESTABLE("delivery.contestable", NotificationPriority.NORMAL, false),
  DISPUTE_OPENED("dispute.opened", NotificationPriority.URGENT, true),
  DISPUTE_DECIDED("dispute.decided", NotificationPriority.URGENT, true),
  REGISTRATION_REVIEWED("registration.reviewed", NotificationPriority.NORMAL, false),
  /** RN-11.2: o sancionado precisa saber, sem opção de desligar. */
  SANCTION_APPLIED("sanction.applied", NotificationPriority.URGENT, true),
  GOAL_COMPLETED("goal.completed", NotificationPriority.NORMAL, false),
  BONUS_GRANTED("bonus.granted", NotificationPriority.NORMAL, false),
  SUPPORT_REPLIED("support.replied", NotificationPriority.NORMAL, false),
  WALLET_EARNING_RELEASED("wallet.earning_released", NotificationPriority.NORMAL, false),
  WITHDRAWAL_SETTLED("withdrawal.settled", NotificationPriority.NORMAL, false);

  private final String contractName;
  private final NotificationPriority priority;
  private final boolean mandatory;

  NotificationType(String contractName, NotificationPriority priority, boolean mandatory) {
    this.contractName = contractName;
    this.priority = priority;
    this.mandatory = mandatory;
  }

  @JsonValue
  public String contractName() {
    return contractName;
  }

  public NotificationPriority priority() {
    return priority;
  }

  /** RF-08.9 — transacional crítico: preferência do usuário não o desliga. */
  public boolean isMandatory() {
    return mandatory;
  }

  public static NotificationType fromContractName(String value) {
    for (NotificationType type : values()) {
      if (type.contractName.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Tipo de notificação desconhecido: " + value);
  }
}
