package com.uaiou.routing.service;

import com.uaiou.routing.RoutingService;
import java.util.List;
import java.util.Optional;

/**
 * Critério de aceite 6 de T-25: sem chave configurada o recurso desliga <strong>de forma
 * limpa</strong> — a aplicação sobe, os chamadores continuam compilando e chamando, e a rota
 * simplesmente vem ausente, pelo mesmo caminho de um provedor fora do ar (RF-25.10).
 *
 * <p>É por isso que a ausência de chave não é validada no arranque como as demais variáveis
 * obrigatórias ({@code EnvironmentValidator}): rota é recurso opcional, não infraestrutura.
 */
public class DisabledRoutingService implements RoutingService {

  @Override
  public Optional<Route> route(List<Point> waypoints) {
    return Optional.empty();
  }
}
