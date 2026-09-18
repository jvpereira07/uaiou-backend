package com.uaiou.maptiles.service;

import java.time.Instant;

/**
 * Um recurso de mapa como o provedor entregou — o corpo fica <strong>como veio</strong>, gzip
 * inclusive, e só é descomprimido se o cliente não aceitar gzip.
 *
 * <p>Corpo vazio é resposta legítima: o provedor devolve 204 para tile sem nada desenhado (oceano,
 * zoom além do dado). Guardar o vazio evita perguntar de novo pelo mesmo nada.
 */
public record CachedResource(byte[] body, String contentType, boolean gzip, Instant storedAt) {

  public boolean empty() {
    return body.length == 0;
  }
}
