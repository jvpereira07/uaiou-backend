package com.uaiou.notifications.service;

import com.uaiou.notifications.dto.DeviceResponse;
import com.uaiou.notifications.dto.RegisterDeviceRequest;
import com.uaiou.notifications.entity.Dispositivo;
import com.uaiou.notifications.repository.DispositivoRepository;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-08.3/RF-08.4 — registro de dispositivo para push. */
@Service
public class DeviceService {

  private final DispositivoRepository dispositivoRepository;

  public DeviceService(DispositivoRepository dispositivoRepository) {
    this.dispositivoRepository = dispositivoRepository;
  }

  /**
   * RF-08.3 — <strong>upsert pelo {@code pushToken}</strong>, nunca insert cego: o mesmo aparelho
   * reinstalado ou passado para outra conta reassocia a linha existente. Duas linhas com o mesmo
   * token entregariam a notificação de um usuário ao outro — cenário real de aparelho compartilhado
   * entre turnos de entregadores.
   */
  @Transactional
  public DeviceResponse register(UUID usuarioId, RegisterDeviceRequest request) {
    Dispositivo dispositivo =
        dispositivoRepository
            .findByPushToken(request.pushToken())
            .map(
                existente -> {
                  existente.reassociar(usuarioId, request.platform(), request.appVersion());
                  return existente;
                })
            .orElseGet(
                () ->
                    dispositivoRepository.save(
                        new Dispositivo(
                            UuidV7.next(),
                            usuarioId,
                            request.platform(),
                            request.pushToken(),
                            request.appVersion())));
    return toResponse(dispositivo);
  }

  @Transactional(readOnly = true)
  public List<DeviceResponse> list(UUID usuarioId) {
    return dispositivoRepository.findByUsuarioId(usuarioId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public void remove(UUID usuarioId, UUID dispositivoId) {
    Dispositivo dispositivo =
        dispositivoRepository
            .findByIdAndUsuarioId(dispositivoId, usuarioId)
            .orElseThrow(
                () -> new NotFoundException("DEVICE_NOT_FOUND", "Dispositivo não encontrado."));
    dispositivoRepository.delete(dispositivo);
  }

  private DeviceResponse toResponse(Dispositivo dispositivo) {
    return new DeviceResponse(
        dispositivo.getId(),
        dispositivo.getPlataforma(),
        dispositivo.getAppVersion(),
        dispositivo.getUltimoUsoEm());
  }
}
