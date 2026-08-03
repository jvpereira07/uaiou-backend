package com.uaiou.notifications.service;

import com.uaiou.notifications.NotificationPriority;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.notifications.dto.NotificationSummary;
import com.uaiou.notifications.entity.Notificacao;
import com.uaiou.notifications.repository.NotificacaoRepository;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.PagingRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** RF-08.5 — inbox: a fonte de verdade das notificações (o push é entrega best-effort). */
@Service
public class NotificationInboxService {

  private final NotificacaoRepository notificacaoRepository;
  private final ObjectMapper objectMapper;

  public NotificationInboxService(
      NotificacaoRepository notificacaoRepository, ObjectMapper objectMapper) {
    this.notificacaoRepository = notificacaoRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public NotificationListResponse list(
      UUID usuarioId, Boolean unread, String type, PagingRequest paging) {
    PageRequest pageable = PageRequest.of(paging.page() - 1, paging.perPage());
    boolean somenteNaoLidas = Boolean.TRUE.equals(unread);

    Page<Notificacao> page;
    if (type != null && somenteNaoLidas) {
      page =
          notificacaoRepository.findByUsuarioIdAndTipoAndLidaEmIsNullOrderByCriadoEmDesc(
              usuarioId, type, pageable);
    } else if (type != null) {
      page =
          notificacaoRepository.findByUsuarioIdAndTipoOrderByCriadoEmDesc(
              usuarioId, type, pageable);
    } else if (somenteNaoLidas) {
      page =
          notificacaoRepository.findByUsuarioIdAndLidaEmIsNullOrderByCriadoEmDesc(
              usuarioId, pageable);
    } else {
      page = notificacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId, pageable);
    }

    return new NotificationListResponse(
        page.getContent().stream().map(this::toSummary).toList(),
        new NotificationListResponse.Meta(
            paging.page(),
            paging.perPage(),
            page.getTotalElements(),
            // Contagem real do usuário, não do recorte: é o badge do app.
            notificacaoRepository.countByUsuarioIdAndLidaEmIsNull(usuarioId)));
  }

  @Transactional
  public NotificationSummary markRead(UUID usuarioId, UUID notificacaoId) {
    Notificacao notificacao =
        notificacaoRepository
            .findByIdAndUsuarioId(notificacaoId, usuarioId)
            // 404 e não 403: notificação alheia não tem a existência revelada (api/README.md).
            .orElseThrow(
                () ->
                    new NotFoundException("NOTIFICATION_NOT_FOUND", "Notificação não encontrada."));
    notificacao.marcarLida();
    return toSummary(notificacao);
  }

  @Transactional
  public long markAllRead(UUID usuarioId) {
    List<Notificacao> naoLidas = notificacaoRepository.findByUsuarioIdAndLidaEmIsNull(usuarioId);
    naoLidas.forEach(Notificacao::marcarLida);
    return naoLidas.size();
  }

  private NotificationSummary toSummary(Notificacao notificacao) {
    return new NotificationSummary(
        notificacao.getId(),
        notificacao.getTipo(),
        notificacao.getPrioridade() == null
            ? NotificationPriority.NORMAL
            : notificacao.getPrioridade(),
        notificacao.getTitulo(),
        notificacao.getCorpo(),
        desserializarPayload(notificacao.getPayload()),
        notificacao.getLidaEm(),
        notificacao.getCriadoEm());
  }

  private Map<String, Object> desserializarPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
  }
}
