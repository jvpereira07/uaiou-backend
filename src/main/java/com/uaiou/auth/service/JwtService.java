package com.uaiou.auth.service;

import com.uaiou.auth.config.JwtProperties;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Emite e decodifica o access token (RF-03.7). Curto de propósito (~15 min, {@code
 * app.jwt.access-ttl-minutes}) — {@code status} viaja nele como cache, nunca como fonte de verdade
 * (RF-03.9).
 */
@Service
public class JwtService {

  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_STATUS = "status";

  private final Key signingKey;
  private final long accessTtlSeconds;

  public JwtService(JwtProperties properties) {
    this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = properties.accessTtlMinutes() * 60;
  }

  public IssuedAccessToken issueAccessToken(UUID userId, Role role, UserStatus status) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(accessTtlSeconds);

    String token =
        Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_STATUS, status.name())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

    return new IssuedAccessToken(token, accessTtlSeconds);
  }

  /** Vazio se o token for inválido, malformado ou expirado — nunca lança para fora deste método. */
  public Optional<AuthenticatedUser> parse(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith((javax.crypto.SecretKey) signingKey)
              .build()
              .parseSignedClaims(token)
              .getPayload();

      UUID userId = UUID.fromString(claims.getSubject());
      Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
      UserStatus status = UserStatus.valueOf(claims.get(CLAIM_STATUS, String.class));
      return Optional.of(new AuthenticatedUser(userId, role, status));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
