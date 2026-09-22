package com.uaiou.feira.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Modo feira (docs/feira/03-implementacao.md). Uma flag só, lida num lugar só: o fluxo da feira
 * vive em controllers próprios e não se espalha em {@code if} pelos serviços do produto. Desligada
 * por padrão — o sistema completo é o comportamento normal, a feira é o desvio.
 *
 * <p>{@code estabelecimentoId} é o "UaiOu", semeado por V28__feira.sql com id determinístico; é
 * propriedade, e não constante no código, para um ambiente conseguir apontar para outra linha sem
 * recompilar.
 */
@ConfigurationProperties(prefix = "app.feira")
public record FeiraProperties(boolean enabled, UUID estabelecimentoId) {}
