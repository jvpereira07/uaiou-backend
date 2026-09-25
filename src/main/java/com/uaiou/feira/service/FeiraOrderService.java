package com.uaiou.feira.service;

import com.uaiou.delivery.service.GeofenceEvaluator;
import com.uaiou.feira.config.FeiraProperties;
import com.uaiou.feira.dto.FeiraDtos.CapturaAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.CapturaResponse;
import com.uaiou.feira.dto.FeiraDtos.CriarPedidoRequest;
import com.uaiou.feira.dto.FeiraDtos.EditarPedidoRequest;
import com.uaiou.feira.dto.FeiraDtos.EstandeRequest;
import com.uaiou.feira.dto.FeiraDtos.EstandeResponse;
import com.uaiou.feira.dto.FeiraDtos.PedidoAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.PedidoResponse;
import com.uaiou.feira.entity.FeiraCaptura;
import com.uaiou.feira.repository.FeiraCapturaRepository;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido-prêmio da feira: publicação pelo admin, vitrine do jogador, captura e entrega
 * (docs/feira/01-fluxos.md, fluxos 3 a 6).
 */
@Service
public class FeiraOrderService {

  /** Cabe em cancelamento_motivo (varchar 30) e separa estes cancelamentos dos do produto. */
  private static final String MOTIVO_DESISTENCIA = "feira_desistencia";

  /** Cancelamento feito pelo operador ao descartar a captura no balcão. */
  private static final String MOTIVO_DESCARTE = "feira_descarte";

  private final PedidoRepository pedidoRepository;
  private final FeiraCapturaRepository capturaRepository;
  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final FeiraProperties properties;
  private final GeofenceEvaluator geofenceEvaluator;
  private final ApplicationEventPublisher events;
  private final FeiraBonusService bonusService;

  public FeiraOrderService(
      PedidoRepository pedidoRepository,
      FeiraCapturaRepository capturaRepository,
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      FeiraProperties properties,
      GeofenceEvaluator geofenceEvaluator,
      ApplicationEventPublisher events,
      FeiraBonusService bonusService) {
    this.bonusService = bonusService;
    this.pedidoRepository = pedidoRepository;
    this.capturaRepository = capturaRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.properties = properties;
    this.geofenceEvaluator = geofenceEvaluator;
    this.events = events;
  }

  /**
   * Onde fica o estande, no mapa. O jogador vai até lá para a coleta, então esta coordenada é o que
   * o app desenha como origem da entrega — e num evento ela só se conhece no dia, montando a mesa,
   * o que é o motivo de isto ser tela e não seed.
   */
  @Transactional(readOnly = true)
  public EstandeResponse estande() {
    return toEstande(estandeUaiOu());
  }

  @Transactional
  public EstandeResponse moverEstande(EstandeRequest request) {
    Estabelecimento uaiou = estandeUaiOu();
    uaiou.atualizarEndereco(null, null, null, null, null, request.lat(), request.longitude());
    uaiou.descreverLocalDaFeira(request.descricao());
    return toEstande(uaiou);
  }

  private static EstandeResponse toEstande(Estabelecimento uaiou) {
    return new EstandeResponse(
        uaiou.getLat(), uaiou.getLongitude(), uaiou.getFeiraDescricaoLocal());
  }

  private Estabelecimento estandeUaiOu() {
    return estabelecimentoRepository
        .findById(properties.estabelecimentoId())
        .orElseThrow(
            () -> new NotFoundException("ESTANDE_NOT_FOUND", "Estabelecimento UaiOu não existe."));
  }

  @Transactional
  public PedidoAdminResponse publicar(CriarPedidoRequest request) {
    UUID id = UuidV7.next();
    Pedido pedido =
        Pedido.paraFeira(
            id,
            numeroDe(id),
            properties.estabelecimentoId(),
            request.lat(),
            request.longitude(),
            request.rotulo().trim(),
            request.recompensa().trim(),
            request.limite(),
            request.codigo().trim());
    return toAdmin(pedidoRepository.saveAndFlush(pedido));
  }

  /**
   * O número do pedido é único por estabelecimento (uk_pedido_numero_por_estabelecimento). O
   * produto serializa uma sequência por loja sob trava; aqui todos os pedidos são da mesma loja e
   * não há nota fiscal para casar, então derivar do próprio id evita a trava.
   *
   * <p>Vem do FIM do UUID, não do começo: em UUID v7 os primeiros dígitos são o relógio, e os 8
   * primeiros só mudam a cada ~65 s — todo pedido criado dentro da mesma janela saía com o mesmo
   * número e batia na unique. A cauda é a parte aleatória.
   */
  private String numeroDe(UUID id) {
    String semTracos = id.toString().replace("-", "");
    return "F-" + semTracos.substring(semTracos.length() - 12);
  }

  @Transactional(readOnly = true)
  public List<PedidoAdminResponse> listarParaAdmin() {
    // Só os modelos: cada captura gera uma instância com a mesma recompensa e o mesmo ponto, e
    // listá-las fazia o painel mostrar o prêmio em dobro — abrindo a cópia, "ninguém pegou ainda".
    // As instâncias aparecem no painel pelas capturas do modelo.
    return pedidoRepository.findByFeiraRecompensaIsNotNullOrderByCriadoEmDesc().stream()
        .filter(Pedido::isFeiraModelo)
        .map(this::toAdmin)
        .toList();
  }

  /**
   * Vitrine do jogador: só o que ainda dá para pegar, mais os que ele mesmo já pegou — deixar os
   * capturados na lista é o que mostra "você já pegou este" em vez de o pedido sumir e parecer bug.
   */
  @Transactional(readOnly = true)
  public List<PedidoResponse> listarParaJogador(UUID jogadorId) {
    Set<UUID> capturados =
        capturaRepository.findByEntregadorIdOrderByCriadoEmDesc(jogadorId).stream()
            .map(FeiraCaptura::getPedidoId)
            .collect(Collectors.toSet());

    return pedidoRepository.findByFeiraRecompensaIsNotNullOrderByCriadoEmDesc().stream()
        .filter(pedido -> pedido.isFeiraCapturavel() || capturados.contains(pedido.getId()))
        .map(pedido -> toJogador(pedido, capturados.contains(pedido.getId())))
        .toList();
  }

  /**
   * A captura instancia um pedido de verdade para o jogador, a partir do modelo. É o que faz o app
   * inteiro funcionar: a partir daqui existe um pedido atribuído, e coleta no estabelecimento,
   * entrega com GPS e histórico são o fluxo do produto, sem nada de feira dentro deles.
   *
   * <p>A trava no modelo serializa a leitura do estoque com o decremento: sem ela, duas capturas
   * simultâneas na última unidade leem o mesmo contador e passam as duas — e falta Bis na mesa.
   */
  @Transactional
  public CapturaResponse capturar(UUID modeloId, UUID jogadorId) {
    Pedido modelo =
        pedidoRepository
            .findByIdForUpdate(modeloId)
            .filter(Pedido::isFeiraModelo)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));

    if (modelo.getFeiraEncerradoEm() != null) {
      throw new ConflictException("PEDIDO_ENCERRADO", "Este pedido já foi encerrado.");
    }
    // Antes do insert: a violação da ux_feira_captura aborta a transação no Postgres, e a consulta
    // no catch já não roda — o jogador via erro genérico. A trava no modelo serializa a checagem.
    if (capturaRepository.existsByPedidoIdAndEntregadorId(modeloId, jogadorId)) {
      throw new ConflictException("JA_CAPTURADO", "Você já pegou este pedido.");
    }
    if (modelo.getFeiraRestantes() <= 0) {
      throw new ConflictException("RECOMPENSA_ESGOTADA", "As recompensas deste pedido acabaram.");
    }

    UUID instanciaId = UuidV7.next();
    Pedido instancia =
        Pedido.instanciaDeFeira(instanciaId, numeroDe(instanciaId), modelo, jogadorId);

    FeiraCaptura captura = new FeiraCaptura(UuidV7.next(), modeloId, jogadorId, instanciaId);
    try {
      // O pedido antes da captura: a FK de pedido_gerado_id exige que ele já exista, e é a unique
      // da captura que decide quem ganhou a corrida — um pedido órfão reverte junto na transação.
      pedidoRepository.saveAndFlush(instancia);
      capturaRepository.saveAndFlush(captura);
    } catch (DataIntegrityViolationException e) {
      // ux_feira_captura: a regra "uma vez por pessoa" é do banco, não da aplicação. Mas só essa
      // violação vira "já capturado": traduzir qualquer falha de integridade nessa mensagem já
      // escondeu uma colisão de número de pedido, que parecia ao jogador uma captura repetida
      // enquanto o defeito real estava em outro lugar.
      if (capturaRepository.existsByPedidoIdAndEntregadorId(modeloId, jogadorId)) {
        throw new ConflictException("JA_CAPTURADO", "Você já pegou este pedido.");
      }
      throw e;
    }

    modelo.capturarNaFeira();

    return new CapturaResponse(
        captura.getId(),
        modeloId,
        instanciaId,
        modelo.getFeiraRecompensa(),
        captura.getCriadoEm(),
        captura.getEntregueEm());
  }

  @Transactional(readOnly = true)
  public List<CapturaResponse> minhasCapturas(UUID jogadorId) {
    List<FeiraCaptura> capturas =
        capturaRepository.findByEntregadorIdOrderByCriadoEmDesc(jogadorId);
    Map<UUID, Pedido> pedidos =
        pedidosDe(capturas.stream().map(FeiraCaptura::getPedidoId).toList());
    return capturas.stream()
        .map(
            captura ->
                new CapturaResponse(
                    captura.getId(),
                    captura.getPedidoId(),
                    captura.getPedidoGeradoId(),
                    pedidos.get(captura.getPedidoId()).getFeiraRecompensa(),
                    captura.getCriadoEm(),
                    captura.getEntregueEm()))
        .toList();
  }

  /**
   * O painel do estande. Além de quem pegou, mostra em que ponto do fluxo cada um está — é isso que
   * o operador precisa para saber quem está na frente dele esperando a coleta ser confirmada.
   */
  @Transactional(readOnly = true)
  public List<CapturaAdminResponse> capturasDoPedido(UUID pedidoId) {
    List<FeiraCaptura> capturas = capturaRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId);
    Map<UUID, Entregador> jogadores =
        entregadorRepository
            .findAllById(capturas.stream().map(FeiraCaptura::getEntregadorId).toList())
            .stream()
            .collect(Collectors.toMap(Entregador::getUsuarioId, Function.identity()));
    Map<UUID, Pedido> gerados =
        pedidosDe(
            capturas.stream()
                .map(FeiraCaptura::getPedidoGeradoId)
                .filter(java.util.Objects::nonNull)
                .toList());

    return capturas.stream()
        .map(
            captura -> {
              Entregador jogador = jogadores.get(captura.getEntregadorId());
              Pedido gerado = gerados.get(captura.getPedidoGeradoId());
              return new CapturaAdminResponse(
                  captura.getId(),
                  captura.getPedidoId(),
                  captura.getPedidoGeradoId(),
                  captura.getEntregadorId(),
                  jogador == null ? null : jogador.getUsuario().getNomeExibicao(),
                  jogador == null ? null : jogador.getUsername(),
                  captura.getDescartadaEm() != null ? "descartada" : situacaoDe(gerado),
                  captura.getDescartadaEm() == null
                      && gerado != null
                      && gerado.getStatus() == OrderStatus.ACCEPTED,
                  captura.getCriadoEm(),
                  captura.getEntregueEm(),
                  captura.getDescartadaEm());
            })
        .toList();
  }

  private String situacaoDe(Pedido gerado) {
    if (gerado == null) return "capturado";
    return switch (gerado.getStatus()) {
      case ACCEPTED -> "aguardando coleta";
      case PICKED_UP -> "a caminho do ponto";
      case FINALIZED, CONTESTABLE_FINALIZED -> "entregue";
      case CANCELLED -> "cancelado";
      default -> gerado.getStatus().toJson();
    };
  }

  /**
   * Coleta confirmada pelo operador no estande (docs/feira/01-fluxos.md). O produto exige que o
   * geofence já tenha visto o entregador na loja (RF-26.8); aqui a conferência é o operador olhando
   * para a pessoa, o que é mais confiável que GPS dentro de um pavilhão — e é por isso que este
   * caminho existe em vez de chamar {@code PickupService.confirmarColeta}.
   */
  @Transactional
  public void confirmarColeta(UUID capturaId) {
    FeiraCaptura captura =
        capturaRepository
            .findById(capturaId)
            .orElseThrow(
                () -> new NotFoundException("CAPTURA_NOT_FOUND", "Captura não encontrada."));

    Pedido pedido = pedidoDaCaptura(captura);
    if (pedido.getStatus() == OrderStatus.PICKED_UP) return; // idempotente: dois cliques, um efeito
    if (pedido.getStatus() != OrderStatus.ACCEPTED) {
      throw new ConflictException(
          "COLETA_FORA_DE_ORDEM", "Este pedido não está aguardando coleta.");
    }

    pedido.confirmarColeta();
    events.publishEvent(
        new OrderLifecycleEvents.PickedUp(pedido.getId(), pedido.getEntregadorId()));
  }

  /**
   * Finalização no ponto, só por geofence: não existe destinatário esperando no salão para ditar um
   * código, então chegar É a prova da entrega. O raio vem de configuração e é generoso de propósito
   * — GPS dentro de pavilhão erra dezenas de metros, e travar o jogo por isso arruinaria a
   * demonstração.
   */
  @Transactional
  public CapturaResponse finalizar(UUID pedidoId, UUID jogadorId, String codigo) {
    Pedido pedido =
        pedidoRepository
            .findByIdForUpdate(pedidoId)
            .filter(Pedido::isFeira)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));

    if (!jogadorId.equals(pedido.getEntregadorId())) {
      throw new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado.");
    }
    if (pedido.getStatus() == OrderStatus.FINALIZED) {
      return respostaDaCapturaDe(pedido);
    }
    if (pedido.getStatus() != OrderStatus.PICKED_UP) {
      throw new ConflictException(
          "COLETA_PENDENTE", "Passe no estande do UaiOu para retirar antes de entregar.");
    }

    Entregador jogador =
        entregadorRepository
            .findById(jogadorId)
            .orElseThrow(
                () -> new NotFoundException("JOGADOR_NOT_FOUND", "Jogador não encontrado."));
    if (!geofenceEvaluator.dentroDoRaio(jogador.getLat(), jogador.getLongitude(), pedido)) {
      throw new ConflictException("FORA_DO_PONTO", "Chegue mais perto do ponto para finalizar.");
    }
    // V33: além do raio, o código escrito no ponto. Pedidos antigos, sem código, só o raio.
    String esperado = pedido.getFeiraCodigo();
    if (esperado != null && (codigo == null || !esperado.equals(codigo.trim()))) {
      throw new ConflictException("CODIGO_INVALIDO", "Código incorreto. Confira no ponto.");
    }

    pedido.finalizar();
    // Cada entrega concluída pode fechar a meta de um bônus (V32).
    bonusService.avaliar(jogadorId);
    return respostaDaCapturaDe(pedido);
  }

  private Pedido pedidoDaCaptura(FeiraCaptura captura) {
    UUID geradoId = captura.getPedidoGeradoId();
    if (geradoId == null) {
      throw new ConflictException(
          "CAPTURA_SEM_PEDIDO", "Esta captura é de antes do fluxo de entrega e não tem pedido.");
    }
    return pedidoRepository
        .findByIdForUpdate(geradoId)
        .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));
  }

  private CapturaResponse respostaDaCapturaDe(Pedido pedido) {
    return capturaRepository
        .findByPedidoGeradoId(pedido.getId())
        .map(
            captura ->
                new CapturaResponse(
                    captura.getId(),
                    captura.getPedidoId(),
                    pedido.getId(),
                    pedido.getFeiraRecompensa(),
                    captura.getCriadoEm(),
                    captura.getEntregueEm()))
        .orElseThrow(() -> new NotFoundException("CAPTURA_NOT_FOUND", "Captura não encontrada."));
  }

  /**
   * Entrega do prêmio físico (fluxo 5). Não mexe no estoque — ele já foi consumido na captura; isto
   * é registro de que o Bis saiu da mesa.
   */
  @Transactional
  public void marcarEntregue(UUID capturaId) {
    FeiraCaptura captura =
        capturaRepository
            .findById(capturaId)
            .orElseThrow(
                () -> new NotFoundException("CAPTURA_NOT_FOUND", "Captura não encontrada."));
    captura.marcarEntregue();
    capturaRepository.save(captura);
  }

  /**
   * O operador tira a captura do balcão: teste, jogador que sumiu, pedido que ficou pendurado. Se o
   * pedido do jogador ainda está em andamento, ele é cancelado junto — senão o app continuaria
   * mandando a pessoa ao ponto de uma entrega que ninguém vai validar. O estoque não volta: quem
   * decide repor a mesa é o operador, e descartar não é desistir.
   */
  @Transactional
  public void descartar(UUID capturaId) {
    FeiraCaptura captura =
        capturaRepository
            .findById(capturaId)
            .orElseThrow(
                () -> new NotFoundException("CAPTURA_NOT_FOUND", "Captura não encontrada."));
    captura.descartar();

    UUID geradoId = captura.getPedidoGeradoId();
    if (geradoId != null) {
      pedidoRepository
          .findByIdForUpdate(geradoId)
          .filter(
              pedido ->
                  pedido.getStatus() == OrderStatus.ACCEPTED
                      || pedido.getStatus() == OrderStatus.PICKED_UP)
          .ifPresent(pedido -> pedido.cancelar(MOTIVO_DESCARTE, null));
    }
  }

  /**
   * O jogador desistiu antes de coletar: a captura deixa de existir e o prêmio volta à mesa.
   *
   * <p>A instância é cancelada, não devolvida à vitrine como no produto: ela é do jogador, e
   * publicada de novo viraria um pedido órfão. Apagar a captura libera a regra "uma vez por pessoa"
   * — quem desistiu pode tentar de novo, como qualquer um. Chamado dentro da transação da
   * desistência, com a instância já travada.
   */
  @Transactional
  public void devolverPorDesistencia(Pedido instancia, String nota) {
    instancia.cancelar(MOTIVO_DESISTENCIA, nota);
    capturaRepository.findByPedidoGeradoId(instancia.getId()).ifPresent(capturaRepository::delete);

    UUID modeloId = instancia.getFeiraOrigemId();
    if (modeloId != null) {
      pedidoRepository.findByIdForUpdate(modeloId).ifPresent(Pedido::devolverCapturaNaFeira);
    }
  }

  @Transactional
  public PedidoAdminResponse editar(UUID pedidoId, EditarPedidoRequest request) {
    // Trava como na captura: o limite é comparado com o contador, que muda a cada captura.
    Pedido modelo =
        pedidoRepository
            .findByIdForUpdate(pedidoId)
            .filter(Pedido::isFeiraModelo)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));
    if (request.limite() < modelo.getFeiraCapturas()) {
      throw new ConflictException(
          "LIMITE_ABAIXO_DAS_CAPTURAS",
          "Já foram " + modelo.getFeiraCapturas() + " capturas; o limite não pode ser menor.");
    }
    modelo.editarNaFeira(
        request.rotulo().trim(),
        request.recompensa().trim(),
        request.limite(),
        request.codigo().trim());
    for (Pedido instancia : pedidoRepository.findByFeiraOrigemId(pedidoId)) {
      instancia.copiarEdicaoDoModelo(modelo);
    }
    return toAdmin(pedidoRepository.save(modelo));
  }

  @Transactional
  public PedidoAdminResponse encerrar(UUID pedidoId) {
    Pedido pedido =
        pedidoRepository
            .findById(pedidoId)
            .filter(Pedido::isFeira)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));
    pedido.encerrarNaFeira();
    return toAdmin(pedidoRepository.save(pedido));
  }

  private Map<UUID, Pedido> pedidosDe(List<UUID> ids) {
    return pedidoRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Pedido::getId, Function.identity()));
  }

  private PedidoResponse toJogador(Pedido pedido, boolean capturado) {
    return new PedidoResponse(
        pedido.getId(),
        pedido.getDestBairro(),
        pedido.getDestLat(),
        pedido.getDestLong(),
        pedido.getFeiraRecompensa(),
        pedido.getFeiraRestantes(),
        pedido.getFeiraEncerradoEm() != null,
        capturado);
  }

  private PedidoAdminResponse toAdmin(Pedido pedido) {
    return new PedidoAdminResponse(
        pedido.getId(),
        pedido.getNumero(),
        pedido.getDestBairro(),
        pedido.getDestLat(),
        pedido.getDestLong(),
        pedido.getFeiraRecompensa(),
        pedido.getFeiraLimite() == null ? 0 : pedido.getFeiraLimite(),
        pedido.getFeiraCapturas(),
        pedido.getFeiraRestantes(),
        pedido.getFeiraEncerradoEm(),
        pedido.getCriadoEm(),
        pedido.getFeiraCodigo());
  }
}
