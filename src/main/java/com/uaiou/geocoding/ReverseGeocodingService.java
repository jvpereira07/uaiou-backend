package com.uaiou.geocoding;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Caminho inverso de {@link com.uaiou.orders.service.GeocodingService}: aquele recebe endereço e
 * quer coordenada; este recebe a coordenada que o usuário marcou no mapa e devolve o endereço —
 * principalmente o <strong>CEP</strong>, que o cliente usa para completar rua, bairro e cidade.
 *
 * <p>Existe no servidor por um motivo concreto: a chave do provedor. Ela já está aqui, configurada
 * para T-25 ({@code ROUTING_API_KEY}), e é uma chave <em>de conta</em>. Colocar uma cópia dela no
 * bundle do app web ou no pacote do app Flutter seria publicar a cota do projeto — quem achar a
 * chave gasta por nós. Com o salto atrás desta interface, nenhum cliente precisa de chave nenhuma.
 *
 * <p><strong>Nenhum método lança.</strong> Mesma regra de {@link com.uaiou.routing.RoutingService}:
 * provedor fora do ar, cota estourada, tempo limite ou recurso desligado por falta de chave
 * devolvem {@link Optional#empty()} — "não sei o endereço daqui" —, nunca erro. Preencher
 * formulário é conveniência; a coordenada marcada continua sendo o dado que vale, e ela já está no
 * cliente antes desta chamada.
 */
public interface ReverseGeocodingService {

  Optional<Address> reverse(BigDecimal lat, BigDecimal lng);

  /**
   * Campos nulos são normais: o provedor devolve o que o mapa tem naquele ponto, e ponto em zona
   * rural costuma vir só com cidade. Quem decide o que fazer com a lacuna é o cliente.
   *
   * @param postalCode só dígitos — {@code 01014000}, nunca {@code 01014-000}. A máscara é decisão
   *     de apresentação, e guardar as duas grafias do mesmo dado só cria divergência.
   */
  record Address(String postalCode, String street, String district, String city, String state) {}
}
