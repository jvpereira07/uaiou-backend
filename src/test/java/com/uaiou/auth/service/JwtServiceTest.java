package com.uaiou.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.config.JwtProperties;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtProperties properties =
      new JwtProperties("segredo-de-teste-com-tamanho-suficiente-para-hs256-funcionar", 15, 30);
  private final JwtService jwtService = new JwtService(properties);

  @Test
  void issuedTokenRoundTripsWithTheClaimsItWasIssuedWith() {
    UUID userId = UUID.randomUUID();

    IssuedAccessToken issued = jwtService.issueAccessToken(userId, Role.COURIER, UserStatus.ACTIVE);
    Optional<AuthenticatedUser> parsed = jwtService.parse(issued.token());

    assertThat(parsed).isPresent();
    assertThat(parsed.get().userId()).isEqualTo(userId);
    assertThat(parsed.get().role()).isEqualTo(Role.COURIER);
    assertThat(parsed.get().status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(issued.expiresInSeconds()).isEqualTo(15 * 60);
  }

  @Test
  void malformedTokenParsesToEmptyInsteadOfThrowing() {
    assertThat(jwtService.parse("isto-nao-eh-um-jwt")).isEmpty();
  }

  @Test
  void tokenSignedWithADifferentSecretIsRejected() {
    SecretKey otherKey =
        Keys.hmacShaKeyFor(
            "outro-segredo-completamente-diferente-do-configurado-no-service"
                .getBytes(StandardCharsets.UTF_8));
    String token =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("role", "COURIER")
            .claim("status", "ACTIVE")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(60)))
            .signWith(otherKey)
            .compact();

    assertThat(jwtService.parse(token)).isEmpty();
  }

  @Test
  void expiredTokenIsRejected() {
    SecretKey key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    String token =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("role", "COURIER")
            .claim("status", "ACTIVE")
            .issuedAt(Date.from(Instant.now().minusSeconds(120)))
            .expiration(Date.from(Instant.now().minusSeconds(60)))
            .signWith(key)
            .compact();

    assertThat(jwtService.parse(token)).isEmpty();
  }
}
