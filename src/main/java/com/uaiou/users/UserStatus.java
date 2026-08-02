package com.uaiou.users;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code usuario.status} no banco é em português (RN-11.1); o JSON usa minúsculas em inglês
 * (api/auth.md: {@code "status": "pending"}). {@link #toJson()}/{@link #fromJson(String)} fazem
 * essa borda; {@link UserStatusConverter} faz a borda com o banco. Nenhum outro código deveria
 * comparar contra a string crua de nenhum dos dois lados.
 */
public enum UserStatus {
  PENDING,
  ACTIVE,
  SUSPENDED,
  BANNED,
  /**
   * Cadastro (ou reenvio de campo verificado) avaliado e negado — T-06/T-07. Volta a {@link
   * #PENDING} no próximo reenvio.
   */
  REJECTED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static UserStatus fromJson(String value) {
    return UserStatus.valueOf(value.toUpperCase());
  }
}
