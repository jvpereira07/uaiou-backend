package com.uaiou.geocoding.config;

import com.uaiou.geocoding.ReverseGeocodingService;
import com.uaiou.geocoding.service.DisabledReverseGeocodingService;
import com.uaiou.geocoding.service.GeoapifyReverseGeocodingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Escolhe a implementação de {@link ReverseGeocodingService} pela configuração — o único lugar do
 * sistema que sabe qual provedor resolve coordenada em endereço. Mesmo desenho de {@link
 * com.uaiou.routing.config.RoutingConfiguration}.
 */
@Configuration
public class ReverseGeocodingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ReverseGeocodingConfiguration.class);

  @Bean
  public ReverseGeocodingService reverseGeocodingService(ReverseGeocodingProperties properties) {
    if (!properties.enabled()) {
      log.warn(
          "Geocodificação inversa desligada: sem chave. GET /geocoding/reverse responde sem"
              + " endereço — o formulário do cliente pede preenchimento à mão.");
      return new DisabledReverseGeocodingService();
    }
    return new GeoapifyReverseGeocodingService(geocodingRestClient(properties), properties);
  }

  /**
   * Cliente próprio, não o global: tempo limite curto <strong>neste</strong> provedor. Herdar o
   * padrão do framework (sem limite) deixaria uma chamada externa segurando a thread de quem está
   * com um formulário aberto esperando os campos preencherem.
   */
  private RestClient geocodingRestClient(ReverseGeocodingProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.timeout());
    factory.setReadTimeout(properties.timeout());

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }
}
