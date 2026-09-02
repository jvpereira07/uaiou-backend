package com.uaiou.routing.config;

import com.uaiou.routing.RoutingService;
import com.uaiou.routing.service.DisabledRoutingService;
import com.uaiou.routing.service.GeoapifyRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Escolhe a implementação de {@link RoutingService} pela configuração — o único lugar do sistema
 * que sabe qual provedor está em uso (RF-25.1).
 */
@Configuration
public class RoutingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RoutingConfiguration.class);

  @Bean
  public RoutingService routingService(RoutingProperties properties) {
    if (!properties.enabled()) {
      log.warn(
          "Rotas desligadas: ROUTING_API_KEY ausente. GET /orders/'{'id'}'/route responde sem"
              + " trajeto — o ciclo de entrega segue normal.");
      return new DisabledRoutingService();
    }
    return new GeoapifyRoutingService(routingRestClient(properties), properties);
  }

  /**
   * Cliente próprio, não o global: RNF-25.1 exige tempo limite curto <strong>neste</strong>
   * provedor, e herdar o padrão do framework (sem limite) deixaria uma chamada externa segurando a
   * thread da requisição do entregador.
   */
  private RestClient routingRestClient(RoutingProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.timeout());
    factory.setReadTimeout(properties.timeout());

    return RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }
}
