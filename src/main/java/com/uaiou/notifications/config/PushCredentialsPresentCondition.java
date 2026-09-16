package com.uaiou.notifications.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Ativa o transporte FCM só quando há credencial. {@code @ConditionalOnProperty} não serve: ele
 * trata string vazia como "presente", e os placeholders do {@code application.yaml} resolvem para
 * vazio quando a variável não existe.
 *
 * <p>Diferente de {@code @ConditionalOnMissingBean} (ver {@code LoggingPushSender}), um {@code
 * Condition} comum é avaliado normalmente em classe varrida por component scan.
 */
public class PushCredentialsPresentCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return StringUtils.hasText(context.getEnvironment().getProperty("app.push.credentials-path"))
        || StringUtils.hasText(context.getEnvironment().getProperty("app.push.credentials-json"));
  }
}
