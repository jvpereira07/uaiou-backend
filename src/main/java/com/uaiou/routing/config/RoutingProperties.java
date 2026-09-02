package com.uaiou.routing.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do provedor de rotas (T-25).
 *
 * @param apiKey RF-25.3 — vem só de variável de ambiente. <strong>Vazia desliga o recurso</strong>
 *     (critério de aceite 6): a aplicação sobe igual, as rotas vêm ausentes.
 * @param mode modo de deslocamento do provedor; motocicleta é o veículo real da praça (RF-25.2).
 * @param timeout RNF-25.1 — curto de propósito: a rota nunca pode segurar a resposta.
 * @param deliveryCacheTtl RF-25.9 — a perna estabelecimento → destino não muda enquanto o pedido
 *     existir; o TTL só existe para a entrada não viver para sempre no Redis.
 * @param pickupCacheTtl RF-25.9 — a perna que varia com quem pergunta. TTL curto + arredondamento
 *     de coordenada; calibrar com consumo real (RF-25.11), não por chute.
 * @param pickupCoordinateScale casas decimais no arredondamento da posição do entregador na chave
 *     de cache. 3 ≈ 110 m — quem andou menos que isso reaproveita a rota de quem passou antes.
 * @param attribution RNF-25.2 — exigência de licença do plano gratuito, devolvida na resposta para
 *     o cliente exibir (A-14, RF-A14.7). Não é escolha de layout.
 */
@ConfigurationProperties(prefix = "app.routing")
public record RoutingProperties(
    String apiKey,
    String baseUrl,
    String mode,
    String language,
    Duration timeout,
    Duration deliveryCacheTtl,
    Duration pickupCacheTtl,
    int pickupCoordinateScale,
    String attribution) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
