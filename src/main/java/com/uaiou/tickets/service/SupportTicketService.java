package com.uaiou.tickets.service;

import com.uaiou.credits.repository.TransacaoCreditoRepository;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.tickets.TicketStatus;
import com.uaiou.tickets.dto.AddMessageRequest;
import com.uaiou.tickets.dto.CreateTicketRequest;
import com.uaiou.tickets.dto.ResolveTicketRequest;
import com.uaiou.tickets.dto.TicketDetail;
import com.uaiou.tickets.dto.TicketSummary;
import com.uaiou.tickets.entity.ChamadoMensagem;
import com.uaiou.tickets.entity.ChamadoSuporte;
import com.uaiou.tickets.repository.ChamadoMensagemRepository;
import com.uaiou.tickets.repository.ChamadoSuporteRepository;
import com.uaiou.users.Role;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-21.1 a RF-21.7/RF-21.10 — chamados de suporte, incluindo a contestação de entrega (T-17). */
@Service
public class SupportTicketService {

  private static final List<String> REFERENCE_TYPES =
      List.of("order", "earning", "credit_transaction");

  private final ChamadoSuporteRepository chamadoRepository;
  private final ChamadoMensagemRepository mensagemRepository;
  private final PedidoRepository pedidoRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final TransacaoCreditoRepository transacaoCreditoRepository;
  private final UsuarioRepository usuarioRepository;
  private final NotificationService notificationService;

  public SupportTicketService(
      ChamadoSuporteRepository chamadoRepository,
      ChamadoMensagemRepository mensagemRepository,
      PedidoRepository pedidoRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      TransacaoCreditoRepository transacaoCreditoRepository,
      UsuarioRepository usuarioRepository,
      NotificationService notificationService) {
    this.chamadoRepository = chamadoRepository;
    this.mensagemRepository = mensagemRepository;
    this.pedidoRepository = pedidoRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.transacaoCreditoRepository = transacaoCreditoRepository;
    this.usuarioRepository = usuarioRepository;
    this.notificationService = notificationService;
  }

  /**
   * RF-21.1/RF-21.2 — chamado e primeira mensagem na MESMA transação; reference validada por
   * propriedade.
   */
  @Transactional
  public TicketDetail create(UUID autorId, CreateTicketRequest request) {
    String referenceType = null;
    UUID referenceId = null;
    if (request.reference() != null && request.reference().id() != null) {
      referenceType = request.reference().type();
      referenceId = request.reference().id();
      validarPropriedadeDaReferencia(autorId, referenceType, referenceId);
    }

    ChamadoSuporte chamado =
        chamadoRepository.save(
            new ChamadoSuporte(
                UuidV7.next(), autorId, request.subject(), referenceType, referenceId));
    mensagemRepository.save(
        new ChamadoMensagem(UuidV7.next(), chamado.getId(), autorId, request.message()));

    return toDetail(chamado, false);
  }

  /** RF-21.2 — só referencia recurso do qual o autor é parte; tipo desconhecido → 400. */
  private void validarPropriedadeDaReferencia(UUID autorId, String type, UUID id) {
    if (!REFERENCE_TYPES.contains(type)) {
      throw new BadRequestException(
          "UNSUPPORTED_REFERENCE_TYPE", "\"" + type + "\" não é um tipo de referência suportado.");
    }
    boolean pertence =
        switch (type) {
          case "order" -> {
            Pedido pedido = pedidoRepository.findById(id).orElse(null);
            yield pedido != null
                && (pedido.pertenceAoEstabelecimento(autorId) || pedido.estaAtribuidoA(autorId));
          }
          case "earning" -> {
            LancamentoFrete lancamento = lancamentoFreteRepository.findById(id).orElse(null);
            yield lancamento != null
                && (lancamento.pertenceAoEntregador(autorId)
                    || lancamento.pertenceAoEstabelecimento(autorId));
          }
          case "credit_transaction" -> {
            var transacao = transacaoCreditoRepository.findById(id).orElse(null);
            yield transacao != null && transacao.getEstabelecimentoId().equals(autorId);
          }
          default -> false;
        };
    if (!pertence) {
      throw new ForbiddenException(
          "NOT_A_PARTY", "Você só referencia recursos dos quais faz parte.");
    }
  }

  /** RF-21.3 — próprios chamados para o autor, fila completa (com filtros) para o admin. */
  @Transactional(readOnly = true)
  public List<TicketSummary> listForAuthor(UUID autorId) {
    return chamadoRepository
        .findByAutorIdOrderByCriadoEmDesc(autorId, PageRequest.of(0, 100))
        .getContent()
        .stream()
        .map(this::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TicketSummary> listForAdmin(
      TicketStatus status, Role authorRole, Instant from, Instant to) {
    List<ChamadoSuporte> base =
        status == null
            ? chamadoRepository.findAll()
            : chamadoRepository
                .findByStatusOrderByCriadoEmAsc(status, PageRequest.of(0, 200))
                .getContent();

    return base.stream()
        .filter(chamado -> from == null || !chamado.getCriadoEm().isBefore(from))
        .filter(chamado -> to == null || !chamado.getCriadoEm().isAfter(to))
        .filter(chamado -> authorRole == null || papelDoAutor(chamado.getAutorId()) == authorRole)
        .map(this::toSummary)
        .toList();
  }

  private Role papelDoAutor(UUID autorId) {
    return usuarioRepository.findById(autorId).map(Usuario::getTipo).orElse(null);
  }

  @Transactional(readOnly = true)
  public TicketDetail get(UUID usuarioId, boolean isAdmin, UUID chamadoId) {
    ChamadoSuporte chamado = chamadoRepository.findById(chamadoId).orElseThrow(this::notFound);
    if (!isAdmin && !chamado.pertenceA(usuarioId)) {
      throw notFound();
    }
    return toDetail(chamado, isAdmin);
  }

  /**
   * RF-21.5 — primeira mensagem do admin move para {@code em_atendimento} e vincula o responsável.
   * Autor não escreve em chamado resolvido (RF-21.5/critério 5) — reabertura é {@code TODO(dono)}.
   */
  @Transactional
  public TicketDetail addMessage(
      UUID usuarioId, boolean isAdmin, UUID chamadoId, AddMessageRequest request) {
    ChamadoSuporte chamado = chamadoRepository.findById(chamadoId).orElseThrow(this::notFound);
    if (!isAdmin && !chamado.pertenceA(usuarioId)) {
      throw notFound();
    }
    if (!isAdmin && chamado.getStatus() == TicketStatus.RESOLVED) {
      throw new BusinessRuleException(
          "TICKET_RESOLVED",
          "Este chamado já foi resolvido. Abra um novo chamado referenciando este.",
          "RN-06.1");
    }

    mensagemRepository.save(
        new ChamadoMensagem(UuidV7.next(), chamadoId, usuarioId, request.message()));
    if (isAdmin) {
      chamado.assumirPeloAdmin(usuarioId);
      chamadoRepository.save(chamado);
      notificarAutor(chamado);
    }

    return toDetail(chamado, isAdmin);
  }

  /** RF-21.6 — encerra com resposta final e, opcionalmente, o ajuste que resolveu o chamado. */
  @Transactional
  public TicketDetail resolve(UUID adminId, UUID chamadoId, ResolveTicketRequest request) {
    ChamadoSuporte chamado = chamadoRepository.findById(chamadoId).orElseThrow(this::notFound);

    mensagemRepository.save(
        new ChamadoMensagem(UuidV7.next(), chamadoId, adminId, request.finalResponse()));
    chamado.assumirPeloAdmin(adminId);
    chamado.resolver(request.adjustmentType(), request.adjustmentId());
    chamadoRepository.save(chamado);

    notificarAutor(chamado);

    return toDetail(chamado, true);
  }

  private void notificarAutor(ChamadoSuporte chamado) {
    boolean resolvido = chamado.getStatus() == TicketStatus.RESOLVED;
    notificationService.publicar(
        chamado.getAutorId(),
        NotificationType.SUPPORT_REPLIED,
        resolvido ? "Chamado resolvido" : "Nova resposta no seu chamado",
        chamado.getAssunto(),
        Map.of("ticketId", chamado.getId().toString()));
  }

  private TicketSummary toSummary(ChamadoSuporte chamado) {
    Usuario autor = usuarioRepository.findById(chamado.getAutorId()).orElse(null);
    return new TicketSummary(
        chamado.getId(),
        chamado.getAssunto(),
        chamado.getStatus(),
        chamado.getAutorId(),
        autor == null ? null : autor.getNomeExibicao(),
        chamado.getReferenciaTipo(),
        chamado.getReferenciaId(),
        contestacaoDeEntrega(chamado),
        chamado.getCriadoEm());
  }

  private TicketDetail toDetail(ChamadoSuporte chamado, boolean isAdmin) {
    Usuario autor = usuarioRepository.findById(chamado.getAutorId()).orElse(null);
    List<ChamadoMensagem> mensagens =
        mensagemRepository.findByChamadoIdOrderByCriadoEmAsc(chamado.getId());

    List<TicketDetail.Message> mensagensDto =
        mensagens.stream()
            .map(
                mensagem -> {
                  Usuario autorMensagem =
                      usuarioRepository.findById(mensagem.getAutorId()).orElse(null);
                  boolean deAdmin = autorMensagem != null && autorMensagem.getTipo() == Role.ADMIN;
                  return new TicketDetail.Message(
                      mensagem.getId(),
                      mensagem.getAutorId(),
                      autorMensagem == null ? null : autorMensagem.getNomeExibicao(),
                      deAdmin,
                      mensagem.getMensagem(),
                      mensagem.getCriadoEm());
                })
            .toList();

    Map<String, LinkRef> supervisionLinks = null;
    if (isAdmin) {
      supervisionLinks = new LinkedHashMap<>();
      supervisionLinks.put("author", LinkRef.get("/api/v1/admin/users/" + chamado.getAutorId()));
      if ("order".equals(chamado.getReferenciaTipo()) && chamado.getReferenciaId() != null) {
        supervisionLinks.put(
            "order", LinkRef.get("/api/v1/admin/orders/" + chamado.getReferenciaId()));
      }
    }

    return new TicketDetail(
        chamado.getId(),
        chamado.getAssunto(),
        chamado.getStatus(),
        chamado.getAutorId(),
        autor == null ? null : autor.getNomeExibicao(),
        chamado.getAdminId(),
        chamado.getReferenciaTipo(),
        chamado.getReferenciaId(),
        chamado.getAjusteTipo(),
        chamado.getAjusteId(),
        contestacaoDeEntrega(chamado),
        chamado.getResolvidoEm(),
        chamado.getCriadoEm(),
        mensagensDto,
        supervisionLinks);
  }

  /** RF-21.10 — destaca na fila o chamado que referencia uma entrega finalizada sem código. */
  private boolean contestacaoDeEntrega(ChamadoSuporte chamado) {
    if (!"order".equals(chamado.getReferenciaTipo()) || chamado.getReferenciaId() == null) {
      return false;
    }
    return pedidoRepository
        .findById(chamado.getReferenciaId())
        .map(pedido -> pedido.getStatus() == OrderStatus.CONTESTABLE_FINALIZED)
        .orElse(false);
  }

  private NotFoundException notFound() {
    return new NotFoundException("TICKET_NOT_FOUND", "Chamado não encontrado.");
  }
}
