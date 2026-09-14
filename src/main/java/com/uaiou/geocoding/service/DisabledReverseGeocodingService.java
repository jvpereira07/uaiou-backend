package com.uaiou.geocoding.service;

import com.uaiou.geocoding.ReverseGeocodingService;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Sem chave configurada o recurso desliga <strong>de forma limpa</strong> — mesma escolha de {@link
 * com.uaiou.routing.service.DisabledRoutingService}: a aplicação sobe, os chamadores continuam
 * compilando, e o endereço vem ausente pelo mesmo caminho de um provedor fora do ar.
 *
 * <p>O formulário do cliente não quebra por isso: ele mostra "não encontramos o endereço deste
 * ponto, preencha à mão" e segue, porque a coordenada — que é o dado que a entrega usa — já foi
 * marcada no mapa antes desta chamada.
 */
public class DisabledReverseGeocodingService implements ReverseGeocodingService {

  @Override
  public Optional<Address> reverse(BigDecimal lat, BigDecimal lng) {
    return Optional.empty();
  }
}
