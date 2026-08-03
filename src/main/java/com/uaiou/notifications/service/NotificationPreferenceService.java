package com.uaiou.notifications.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.dto.NotificationPreferencesResponse;
import com.uaiou.notifications.dto.UpdateNotificationPreferencesRequest;
import com.uaiou.notifications.entity.PreferenciaNotificacao;
import com.uaiou.notifications.repository.PreferenciaNotificacaoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-08.9 — preferências por canal, com os transacionais críticos sempre entregues. */
@Service
public class NotificationPreferenceService {

  private final PreferenciaNotificacaoRepository repository;

  public NotificationPreferenceService(PreferenciaNotificacaoRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public NotificationPreferencesResponse get(UUID usuarioId) {
    return toResponse(
        repository.findById(usuarioId).orElseGet(() -> new PreferenciaNotificacao(usuarioId)));
  }

  @Transactional
  public NotificationPreferencesResponse update(
      UUID usuarioId, UpdateNotificationPreferencesRequest request) {
    if (request.channels() == null) {
      throw new BusinessRuleException("MISSING_FIELD", "\"channels\" é obrigatório.", "RN-08.9");
    }
    PreferenciaNotificacao preferencia =
        repository.findById(usuarioId).orElseGet(() -> new PreferenciaNotificacao(usuarioId));
    preferencia.atualizar(
        request.channels().push(), request.channels().email(), request.channels().sms());
    return toResponse(repository.save(preferencia));
  }

  private NotificationPreferencesResponse toResponse(PreferenciaNotificacao preferencia) {
    Map<String, Boolean> channels = new LinkedHashMap<>();
    channels.put("push", preferencia.isPush());
    channels.put("email", preferencia.isEmail());
    channels.put("sms", preferencia.isSms());

    // RF-08.9: a interface precisa saber o que NÃO oferecer como desligável, em vez de mostrar um
    // botão que o servidor ignora.
    List<String> mandatory =
        Arrays.stream(NotificationType.values())
            .filter(NotificationType::isMandatory)
            .map(NotificationType::contractName)
            .toList();

    return new NotificationPreferencesResponse(channels, mandatory);
  }
}
