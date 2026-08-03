package com.uaiou.presence.service;

import com.uaiou.presence.CourierPresence;
import com.uaiou.presence.config.PresenceProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * RF-10.6 — réplica da presença em Redis para o motor de elegibilidade (T-11) não bater no
 * PostgreSQL a cada listagem.
 *
 * <p><strong>Cache, nunca fonte de verdade.</strong> Todo método engole qualquer falha do Redis e
 * devolve "não sei" ({@link Optional#empty()}) em vez de propagar exceção — o critério de aceite 7
 * de T-10 é explícito: queda do Redis é degradação (o chamador cai no banco), não erro para o
 * cliente. Por isso nenhuma assinatura aqui lança.
 *
 * <p>Desenho das chaves:
 *
 * <ul>
 *   <li>{@code presenca:pos:{id}} → {@code lat;long;accuracy;epochMillis}, com TTL igual ao limite
 *       de frescor (RF-10.7). É o TTL que faz posição velha sumir sozinha, sem varredura.
 *   <li>{@code presenca:disponiveis} → SET de ids disponíveis. Não expira por item (SET não tem TTL
 *       por membro), então um id pode sobreviver ao seu {@code pos:} — é intencional: a leitura
 *       cruza os dois e descarta quem não tem posição viva, que é exatamente o critério de aceite 6
 *       ("ignora entregador com posição velha, mesmo com disponivel = true"). A reconciliação
 *       definitiva é do job (RF-10.8).
 * </ul>
 */
@Component
public class PresenceCache {

  private static final Logger log = LoggerFactory.getLogger(PresenceCache.class);

  private static final String AVAILABLE_KEY = "presenca:disponiveis";
  private static final String POSITION_KEY_PREFIX = "presenca:pos:";

  private final StringRedisTemplate redis;
  private final PresenceProperties properties;

  public PresenceCache(StringRedisTemplate redis, PresenceProperties properties) {
    this.redis = redis;
    this.properties = properties;
  }

  public void savePosition(CourierPresence presence) {
    execute(
        "gravar posição",
        () -> {
          redis
              .opsForValue()
              .set(positionKey(presence.courierId()), serialize(presence), properties.freshness());
          return null;
        });
  }

  public void markAvailable(UUID courierId) {
    execute("marcar disponível", () -> redis.opsForSet().add(AVAILABLE_KEY, courierId.toString()));
  }

  public void markUnavailable(UUID courierId) {
    execute(
        "marcar indisponível", () -> redis.opsForSet().remove(AVAILABLE_KEY, courierId.toString()));
  }

  /**
   * Disponíveis com posição ainda viva. {@link Optional#empty()} significa "o cache não respondeu"
   * — distinto de {@code Optional.of(List.of())}, que é "respondeu: ninguém". Sem essa distinção o
   * chamador não teria como saber se deve degradar para o banco.
   */
  public Optional<List<CourierPresence>> findAvailableWithFreshPosition() {
    return execute(
        "listar disponíveis",
        () -> {
          Set<String> members = redis.opsForSet().members(AVAILABLE_KEY);
          if (members == null || members.isEmpty()) {
            return List.of();
          }
          // Fixa a ordem UMA vez: o multiGet devolve os valores posicionalmente, então ids e chaves
          // precisam vir da mesma lista, não de duas iterações separadas sobre o Set.
          List<String> orderedIds = new ArrayList<>(members);
          List<String> keys = orderedIds.stream().map(id -> POSITION_KEY_PREFIX + id).toList();
          List<String> raw = redis.opsForValue().multiGet(keys);
          if (raw == null) {
            return List.of();
          }

          List<CourierPresence> presences = new ArrayList<>();
          for (int i = 0; i < orderedIds.size(); i++) {
            String value = i < raw.size() ? raw.get(i) : null;
            // Ausente = TTL expirou = posição velha. É assim que o critério de aceite 6 se cumpre
            // no
            // caminho do cache.
            if (value != null) {
              deserialize(UUID.fromString(orderedIds.get(i)), value).ifPresent(presences::add);
            }
          }
          return presences;
        });
  }

  private String positionKey(UUID courierId) {
    return POSITION_KEY_PREFIX + courierId;
  }

  private String serialize(CourierPresence presence) {
    return presence.lat()
        + ";"
        + presence.longitude()
        + ";"
        + (presence.accuracy() == null ? "" : presence.accuracy())
        + ";"
        + presence.updatedAt().toEpochMilli();
  }

  private Optional<CourierPresence> deserialize(UUID courierId, String value) {
    try {
      String[] parts = value.split(";", -1);
      if (parts.length != 4) {
        return Optional.empty();
      }
      return Optional.of(
          new CourierPresence(
              courierId,
              new BigDecimal(parts[0]),
              new BigDecimal(parts[1]),
              parts[2].isEmpty() ? null : new BigDecimal(parts[2]),
              Instant.ofEpochMilli(Long.parseLong(parts[3]))));
    } catch (RuntimeException e) {
      // Formato inesperado (deploy antigo, chave escrita à mão) é tratado como cache miss, nunca
      // como
      // erro: o banco resolve.
      log.warn("Entrada de presença ilegível no cache para o entregador {}.", courierId, e);
      return Optional.empty();
    }
  }

  private <T> Optional<T> execute(String acao, CacheCall<T> call) {
    try {
      return Optional.ofNullable(call.run());
    } catch (RuntimeException e) {
      log.warn("Cache de presença indisponível ao {} — seguindo pelo PostgreSQL.", acao, e);
      return Optional.empty();
    }
  }

  @FunctionalInterface
  private interface CacheCall<T> {
    T run();
  }
}
