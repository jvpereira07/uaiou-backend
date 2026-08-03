package com.uaiou.notifications.service;

import com.uaiou.notifications.entity.Dispositivo;
import com.uaiou.notifications.entity.Notificacao;
import java.util.List;

/**
 * Transporte de push. Interface separada porque é a <strong>única</strong> parte de T-08 que
 * depende de infraestrutura externa (FCM/APNs): exige credencial de provedor ({@code
 * PUSH_PROVIDER_CREDENTIALS_PATH} já previsto no docker-compose), que não existe neste ambiente.
 * Todo o resto de T-08 — persistência, inbox, catálogo, preferências, fan-out — é real e não
 * depende disto.
 *
 * <p>RF-08.2: o retorno NÃO é sucesso/fracasso da notificação, e sim quais tokens o provedor
 * rejeitou. A notificação já está persistida antes de chegar aqui; push é entrega best-effort.
 */
public interface PushSender {

  /**
   * @return tokens que o provedor reportou como inválidos, para remoção automática (RF-08.4).
   */
  List<String> send(Notificacao notificacao, List<Dispositivo> destinos);
}
