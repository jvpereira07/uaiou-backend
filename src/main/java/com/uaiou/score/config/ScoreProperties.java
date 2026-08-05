package com.uaiou.score.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RF-20.10 — pesos versionados: mudar o valor de {@code weightsVersion} é como o sistema documenta
 * "o número mudou porque a fórmula mudou", não porque o comportamento do usuário mudou.
 *
 * @param weightsVersion identificador gravado junto de cada score calculado.
 * @param inactiveReviewWeight peso de uma avaliação {@code ativa = false} (padrão positivo, T-19)
 *     na média — menor que 1.0, nunca zero: RF-20 critério 5 pede "peso reduzido", não ausência.
 */
@ConfigurationProperties(prefix = "app.score")
public record ScoreProperties(String weightsVersion, double inactiveReviewWeight) {}
