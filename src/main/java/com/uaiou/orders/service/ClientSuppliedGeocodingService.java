package com.uaiou.orders.service;

import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Implementação da v1: confia nas coordenadas que o cliente já resolveu (o contrato de {@code POST
 * /orders} em api/pedidos.md carrega {@code lat}/{@code lng} dentro de {@code destination} — o app
 * usa um seletor de endereço e manda o ponto escolhido).
 *
 * <p>Decisão registrada, não omissão: o provedor de geocodificação de servidor está 🟡 em aberto na
 * T-11, e escolher um aqui seria inventar decisão de fornecedor (chave de API, custo por chamada,
 * cache, modo de falha na rota de criação). O que o servidor faz é o que consegue fazer com
 * honestidade — validar que o ponto veio e é plausível — e o resto fica atrás de {@link
 * GeocodingService} para trocar sem tocar em chamador.
 *
 * <p>Consequência assumida: um ponto <em>sintaticamente</em> válido mas errado (cliente com bug)
 * passa. Só um provedor de servidor cruzando endereço × coordenada pega isso, e é exatamente essa a
 * decisão em aberto.
 */
@Service
public class ClientSuppliedGeocodingService implements GeocodingService {

  private static final BigDecimal LAT_MIN = new BigDecimal("-90");
  private static final BigDecimal LAT_MAX = new BigDecimal("90");
  private static final BigDecimal LONG_MIN = new BigDecimal("-180");
  private static final BigDecimal LONG_MAX = new BigDecimal("180");

  @Override
  public Coordinates resolve(DestinationRequest destination) {
    if (destination.lat() == null || destination.lng() == null) {
      throw ungeocodable("O endereço precisa vir com coordenadas resolvidas (lat/lng).");
    }
    if (foraDoIntervalo(destination.lat(), LAT_MIN, LAT_MAX)
        || foraDoIntervalo(destination.lng(), LONG_MIN, LONG_MAX)) {
      throw ungeocodable("As coordenadas enviadas não correspondem a um ponto válido no globo.");
    }
    // (0,0) é o "Null Island": quase sempre resultado de campo não preenchido, nunca um destino
    // real
    // de entrega nesta praça.
    if (destination.lat().signum() == 0 && destination.lng().signum() == 0) {
      throw ungeocodable("As coordenadas enviadas não correspondem a um endereço real.");
    }
    return new Coordinates(destination.lat(), destination.lng());
  }

  private boolean foraDoIntervalo(BigDecimal valor, BigDecimal min, BigDecimal max) {
    return valor.compareTo(min) < 0 || valor.compareTo(max) > 0;
  }

  private BusinessRuleException ungeocodable(String mensagem) {
    return new BusinessRuleException("UNGEOCODABLE_ADDRESS", mensagem, "RN-08.1");
  }
}
