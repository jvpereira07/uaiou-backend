package com.uaiou.reviews.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RF-19 risco documentado: "prazo de avaliação — definir junto do prazo de contestação, para os
 * dois ciclos fecharem juntos". Config própria (não reaproveita {@code app.delivery.contestable-
 * window} diretamente) para poder divergir depois sem acoplar os dois domínios.
 *
 * @param window prazo para avaliar após a finalização, depois do qual o job cria o padrão positivo
 *     para o lado omisso (RF-19.5) e a leitura cruzada libera (RF-19.7).
 * @param commentMaxLength limite de tamanho do comentário (RF-19.4).
 */
@ConfigurationProperties(prefix = "app.reviews")
public record ReviewsProperties(Duration window, int commentMaxLength) {}
