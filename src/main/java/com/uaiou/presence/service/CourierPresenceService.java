package com.uaiou.presence.service;

import com.uaiou.presence.CourierPresence;
import com.uaiou.presence.config.PresenceProperties;
import com.uaiou.presence.dto.AvailabilityResponse;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-10.1 a RF-10.7 e RF-10.9 — presença do entregador: disponibilidade e última posição. */
@Service
public class CourierPresenceService {

  private final UsuarioRepository usuarioRepository;
  private final EntregadorRepository entregadorRepository;
  private final PresenceCache presenceCache;
  private final PresenceProperties properties;

  public CourierPresenceService(
      UsuarioRepository usuarioRepository,
      EntregadorRepository entregadorRepository,
      PresenceCache presenceCache,
      PresenceProperties properties) {
    this.usuarioRepository = usuarioRepository;
    this.entregadorRepository = entregadorRepository;
    this.presenceCache = presenceCache;
    this.properties = properties;
  }

  /**
   * RF-10.1/RF-10.2/RF-10.3 — ligar exige conta ativa (403) e posição recente (422); desligar não
   * exige nada além de ser o dono, e nunca toca em pedido em andamento.
   */
  @Transactional
  public AvailabilityResponse setAvailability(UUID courierId, boolean available) {
    Entregador entregador = requireEntregador(courierId);

    if (!available) {
      entregador.ficarIndisponivel();
      presenceCache.markUnavailable(courierId);
      return toResponse(entregador);
    }

    // RF-10.1: só conta ativa fica disponível. O middleware de escrita (T-03) já barra
    // suspenso/banido; pendente e rejeitado chegam até aqui e precisam ser barrados aqui.
    Usuario usuario = requireUsuario(courierId);
    if (usuario.getStatus() != UserStatus.ACTIVE) {
      throw new ForbiddenException(
          "ACCOUNT_NOT_ACTIVE", "Só uma conta ativa pode ficar disponível para entregas.");
    }

    // RF-10.2/RN-01.1: disponível sem posição não pode ser redirecionado e ainda distorce a métrica
    // de cobertura.
    if (!entregador.temPosicaoRecente(properties.freshness(), Instant.now())) {
      throw new BusinessRuleException(
          "LOCATION_REQUIRED",
          "Envie sua posição atual antes de ficar disponível.",
          "RN-01.1",
          Map.of("freshness", properties.freshness().toString()));
    }

    entregador.ficarDisponivel();
    presenceCache.markAvailable(courierId);
    presenceCache.savePosition(toPresence(entregador));
    return toResponse(entregador);
  }

  /**
   * RF-10.4/RF-10.5/RF-10.9 — rota de maior volume do sistema: uma escrita, sem leitura de
   * elegibilidade, sem evento síncrono. O cache só é reescrito para quem está disponível — posição
   * de entregador offline não interessa ao motor de elegibilidade e só gastaria memória do Redis.
   */
  @Transactional
  public void updateLocation(UUID courierId, UpdateLocationRequest request) {
    Entregador entregador = requireEntregador(courierId);
    entregador.atualizarLocalizacao(request.lat(), request.lng(), request.accuracy());

    if (entregador.isDisponivel()) {
      presenceCache.savePosition(toPresence(entregador));
    }
  }

  /**
   * "A posição DESTE entregador ainda vale?" — pergunta pontual, respondida direto na fonte de
   * verdade. Deliberadamente não passa pelo cache: o Redis existe para a listagem de muitos
   * (RF-10.6), e varrer a lista global para achar um id seria O(n) por uma resposta O(1), além de
   * fazer a resposta depender do estado do cache em vez do estado real.
   */
  @Transactional(readOnly = true)
  public boolean hasFreshPresence(UUID courierId) {
    return entregadorRepository
        .findById(courierId)
        .map(
            entregador ->
                entregador.isDisponivel()
                    && entregador.temPosicaoRecente(properties.freshness(), Instant.now()))
        .orElse(false);
  }

  /**
   * RF-10.6/RF-10.7 — o que T-11 vai consumir. Caminho feliz responde do Redis; qualquer falha do
   * cache degrada para o PostgreSQL (critério de aceite 7), e o corte por frescor é o mesmo nos
   * dois caminhos (critério de aceite 6).
   */
  @Transactional(readOnly = true)
  public List<CourierPresence> findEligibleCouriers() {
    return presenceCache.findAvailableWithFreshPosition().orElseGet(this::findEligibleFromDatabase);
  }

  private List<CourierPresence> findEligibleFromDatabase() {
    Instant limite = Instant.now().minus(properties.freshness());
    return entregadorRepository.findDisponiveisComPosicaoDesde(limite).stream()
        .map(this::toPresence)
        .toList();
  }

  private CourierPresence toPresence(Entregador entregador) {
    return new CourierPresence(
        entregador.getUsuarioId(),
        entregador.getLat(),
        entregador.getLongitude(),
        entregador.getAccuracy(),
        entregador.getLocalizacaoEm());
  }

  private AvailabilityResponse toResponse(Entregador entregador) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    if (entregador.isDisponivel()) {
      // 🖼 Ficar disponível leva direto para a lista de pedidos abertos (api/usuarios.md).
      links.put("openOrders", LinkRef.get("/api/v1/orders?status=published"));
    }
    return new AvailabilityResponse(
        entregador.isDisponivel(), entregador.getDisponivelDesde(), links);
  }

  private Entregador requireEntregador(UUID courierId) {
    return entregadorRepository
        .findById(courierId)
        .orElseThrow(
            () ->
                new NotFoundException("COURIER_NOT_FOUND", "Perfil de entregador não encontrado."));
  }

  private Usuario requireUsuario(UUID courierId) {
    return usuarioRepository
        .findById(courierId)
        .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado."));
  }
}
