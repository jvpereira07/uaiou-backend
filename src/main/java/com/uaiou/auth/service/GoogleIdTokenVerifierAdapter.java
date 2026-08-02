package com.uaiou.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.uaiou.auth.config.GoogleAuthProperties;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verificação real: assinatura, emissor e {@code aud} conferidos contra as chaves públicas do
 * Google (JWKS), cacheadas e renovadas pela própria biblioteca — nada disso é feito à mão aqui.
 */
@Component
public class GoogleIdTokenVerifierAdapter implements GoogleIdTokenVerifierPort {

  private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierAdapter.class);

  private final GoogleIdTokenVerifier verifier;

  public GoogleIdTokenVerifierAdapter(GoogleAuthProperties properties) {
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(properties.clientId()))
            .build();
  }

  @Override
  public Optional<GoogleIdentity> verify(String idToken) {
    try {
      GoogleIdToken token = verifier.verify(idToken);
      if (token == null) {
        return Optional.empty();
      }
      GoogleIdToken.Payload payload = token.getPayload();
      return Optional.of(
          new GoogleIdentity(
              payload.getSubject(),
              payload.getEmail(),
              Boolean.TRUE.equals(payload.getEmailVerified())));
    } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
      log.warn("Falha ao verificar ID token do Google: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
