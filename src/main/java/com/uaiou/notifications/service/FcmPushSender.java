package com.uaiou.notifications.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import com.uaiou.notifications.NotificationPriority;
import com.uaiou.notifications.config.PushCredentialsPresentCondition;
import com.uaiou.notifications.entity.Dispositivo;
import com.uaiou.notifications.entity.Notificacao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T-08 — transporte real via Firebase Cloud Messaging (Android, iOS e Web pelo mesmo token FCM).
 *
 * <p>Contrato com os clientes (app e web), que dependem destes nomes:
 *
 * <ul>
 *   <li>{@code data}: {@code notificationId}, {@code type}, {@code priority} e cada chave do
 *       payload da notificação (FCM só aceita string — objetos vão serializados em JSON).
 *   <li>Canal Android: {@code urgente} para {@link NotificationPriority#URGENT}, {@code geral} para
 *       o resto. O app cria os dois com esses ids.
 * </ul>
 *
 * <p>RF-08.8: urgente sai com prioridade alta no Android, {@code apns-priority: 10} e {@code
 * interruption-level: time-sensitive} no iOS, {@code Urgency: high} no Web Push — é o que atravessa
 * modo soneca / economia de bateria.
 */
@Component
@Primary
@Conditional(PushCredentialsPresentCondition.class)
public class FcmPushSender implements PushSender {

  private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);

  /** Limite do FCM por chamada multicast. */
  private static final int LOTE_MAXIMO = 500;

  /** Identificador de navegador do Flutter Web ({@code identificador_dispositivo.dart}). */
  private static final Pattern IDENTIFICADOR_LOCAL = Pattern.compile("[0-9a-f]{32}");

  static final String CANAL_URGENTE = "urgente";
  static final String CANAL_GERAL = "geral";

  private final FirebaseMessaging firebaseMessaging;
  private final ObjectMapper objectMapper;

  public FcmPushSender(FirebaseMessaging firebaseMessaging, ObjectMapper objectMapper) {
    this.firebaseMessaging = firebaseMessaging;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<String> send(Notificacao notificacao, List<Dispositivo> destinos) {
    if (destinos.isEmpty()) {
      return List.of();
    }
    List<String> invalidos = new ArrayList<>();
    List<String> tokens = new ArrayList<>();
    for (Dispositivo destino : destinos) {
      // Antes do FCM o app registrava um identificador local (32 hex) como "token". Enviar para ele
      // só gera erro a cada notificação; devolver como inválido faz o NotificationService limpar.
      if (IDENTIFICADOR_LOCAL.matcher(destino.getPushToken()).matches()) {
        invalidos.add(destino.getPushToken());
      } else {
        tokens.add(destino.getPushToken());
      }
    }
    for (int inicio = 0; inicio < tokens.size(); inicio += LOTE_MAXIMO) {
      List<String> lote = tokens.subList(inicio, Math.min(inicio + LOTE_MAXIMO, tokens.size()));
      invalidos.addAll(enviarLote(notificacao, lote));
    }
    return invalidos;
  }

  private List<String> enviarLote(Notificacao notificacao, List<String> lote) {
    BatchResponse resposta;
    try {
      resposta = firebaseMessaging.sendEachForMulticast(montar(notificacao, lote));
    } catch (FirebaseMessagingException e) {
      // Falha do lote inteiro (credencial, rede, cota): nenhum token é culpado. Quem registra é o
      // NotificationService — aqui só não pode virar "todos os aparelhos são inválidos".
      throw new IllegalStateException("FCM recusou o lote: " + e.getMessagingErrorCode(), e);
    }

    List<String> invalidos = new ArrayList<>();
    List<SendResponse> respostas = resposta.getResponses();
    for (int i = 0; i < respostas.size(); i++) {
      SendResponse item = respostas.get(i);
      if (item.isSuccessful()) {
        continue;
      }
      FirebaseMessagingException erro = item.getException();
      if (tokenMorto(erro)) {
        invalidos.add(lote.get(i));
      } else {
        log.warn(
            "push {} não entregue a um dispositivo: {} — {}",
            notificacao.getId(),
            erro == null ? "sem detalhe" : erro.getMessagingErrorCode(),
            erro == null ? "" : erro.getMessage());
      }
    }
    return invalidos;
  }

  /**
   * RF-08.4 — só descarta o token quando o erro é SOBRE o token. {@code INVALID_ARGUMENT} também
   * cobre payload malformado; sem olhar a mensagem, um bug de montagem apagaria todos os aparelhos.
   */
  private static boolean tokenMorto(FirebaseMessagingException erro) {
    if (erro == null) {
      return false;
    }
    MessagingErrorCode codigo = erro.getMessagingErrorCode();
    if (codigo == MessagingErrorCode.UNREGISTERED
        || codigo == MessagingErrorCode.SENDER_ID_MISMATCH) {
      return true;
    }
    return codigo == MessagingErrorCode.INVALID_ARGUMENT
        && erro.getMessage() != null
        && erro.getMessage().toLowerCase().contains("registration token");
  }

  private MulticastMessage montar(Notificacao notificacao, List<String> tokens) {
    boolean urgente = notificacao.getPrioridade() == NotificationPriority.URGENT;
    String titulo = notificacao.getTitulo();
    String corpo = notificacao.getCorpo() == null ? "" : notificacao.getCorpo();

    return MulticastMessage.builder()
        .addAllTokens(tokens)
        .setNotification(Notification.builder().setTitle(titulo).setBody(corpo).build())
        .putAllData(dados(notificacao))
        .setAndroidConfig(
            AndroidConfig.builder()
                .setPriority(urgente ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                .setNotification(
                    AndroidNotification.builder()
                        .setChannelId(urgente ? CANAL_URGENTE : CANAL_GERAL)
                        .setTag(notificacao.getId().toString())
                        .setDefaultSound(true)
                        .build())
                .build())
        .setApnsConfig(
            ApnsConfig.builder()
                .putHeader("apns-priority", urgente ? "10" : "5")
                .setAps(
                    Aps.builder()
                        .setSound("default")
                        .putCustomData("interruption-level", urgente ? "time-sensitive" : "active")
                        .build())
                .build())
        .setWebpushConfig(
            WebpushConfig.builder()
                .putHeader("Urgency", urgente ? "high" : "normal")
                .setNotification(
                    WebpushNotification.builder()
                        .setTitle(titulo)
                        .setBody(corpo)
                        .setTag(notificacao.getId().toString())
                        .setRequireInteraction(urgente)
                        .build())
                .build())
        .build();
  }

  private Map<String, String> dados(Notificacao notificacao) {
    Map<String, String> dados = new HashMap<>();
    if (notificacao.getPayload() != null) {
      JsonNode raiz = objectMapper.readTree(notificacao.getPayload());
      for (Map.Entry<String, JsonNode> campo : raiz.properties()) {
        JsonNode valor = campo.getValue();
        if (!valor.isNull()) {
          dados.put(campo.getKey(), valor.isString() ? valor.asString() : valor.toString());
        }
      }
    }
    // Depois do payload: nenhum campo do evento pode sobrescrever a identidade da notificação.
    dados.put("notificationId", notificacao.getId().toString());
    dados.put("type", notificacao.getTipo());
    dados.put("priority", notificacao.getPrioridade().toJson());
    return dados;
  }
}
