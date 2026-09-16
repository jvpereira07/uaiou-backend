package com.uaiou.notifications.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * T-08 — cliente FCM. Só existe com credencial (ver {@link PushCredentialsPresentCondition}).
 * Credencial presente e inválida derruba o boot de propósito: subir "funcionando" com push
 * silenciosamente morto é o cenário que faz a contingência falhar sem ninguém perceber.
 */
@Configuration
@Conditional(PushCredentialsPresentCondition.class)
public class FirebaseConfig {

  @Bean
  FirebaseMessaging firebaseMessaging(PushProperties properties) {
    try (InputStream credencial = abrirCredencial(properties)) {
      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(credencial))
              // O transporte padrão (Apache HttpClient 5, na versão gerenciada pelo Spring Boot)
              // já descomprime a resposta; o cliente do Google tenta descomprimir de novo e toda
              // resposta de ERRO do FCM estoura "Not in GZIP format". O código do erro some,
              // token morto nunca é removido e o log não diz o motivo — visto em produção.
              .setHttpTransport(new NetHttpTransport())
              .build();
      FirebaseApp app =
          FirebaseApp.getApps().stream()
              .filter(existente -> FirebaseApp.DEFAULT_APP_NAME.equals(existente.getName()))
              .findFirst()
              .orElseGet(() -> FirebaseApp.initializeApp(options));
      return FirebaseMessaging.getInstance(app);
    } catch (IOException e) {
      throw new UncheckedIOException("Credencial do Firebase ilegível (app.push.*).", e);
    }
  }

  private static InputStream abrirCredencial(PushProperties properties) throws IOException {
    if (StringUtils.hasText(properties.credentialsJson())) {
      return new ByteArrayInputStream(
          properties.credentialsJson().getBytes(StandardCharsets.UTF_8));
    }
    return new FileInputStream(properties.credentialsPath());
  }
}
