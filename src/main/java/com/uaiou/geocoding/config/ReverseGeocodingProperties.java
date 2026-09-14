package com.uaiou.geocoding.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do salto coordenada → CEP.
 *
 * @param apiKey mesma chave Geoapify de T-25. O valor padrão em {@code application.yaml} é o
 *     próprio {@code ROUTING_API_KEY}: é uma conta só no provedor, e duplicar a variável de
 *     ambiente convidaria alguém a girar uma e esquecer a outra. {@code GEOCODING_API_KEY} existe
 *     para quem quiser separar as cotas depois, sem forçar ninguém a isso agora. <strong>Vazia
 *     desliga o recurso</strong>, como em rotas: a aplicação sobe igual e o endereço vem ausente.
 * @param timeout curto pelo mesmo motivo de RNF-25.1 — isto roda enquanto alguém olha um formulário
 *     esperando os campos preencherem, não pode segurar a requisição.
 */
@ConfigurationProperties(prefix = "app.geocoding")
public record ReverseGeocodingProperties(
    String apiKey, String baseUrl, String language, Duration timeout) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
