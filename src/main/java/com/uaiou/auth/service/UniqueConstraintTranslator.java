package com.uaiou.auth.service;

import com.uaiou.shared.error.ConflictException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unicidade é checada pelo banco, não por pré-consulta na aplicação (api/auth.md, "validar só na
 * aplicação abre corrida entre dois cadastros simultâneos") — este helper traduz a violação de
 * {@code UNIQUE} do Postgres para o código de erro e o campo em conflito (critério de aceite 2),
 * lendo o NOME da constraint diretamente da exceção, não o texto livre da mensagem.
 */
final class UniqueConstraintTranslator {

  private static final Logger log = LoggerFactory.getLogger(UniqueConstraintTranslator.class);

  private static final Map<String, String[]> CONSTRAINT_TO_CODE_AND_FIELD =
      Map.of(
          "uk_usuario_login", new String[] {"LOGIN_ALREADY_TAKEN", "login"},
          "uk_usuario_email", new String[] {"EMAIL_ALREADY_TAKEN", "email"},
          "uk_estabelecimento_cnpj", new String[] {"CNPJ_ALREADY_TAKEN", "cnpj"},
          "uk_entregador_cpf", new String[] {"CPF_ALREADY_TAKEN", "cpf"});

  private UniqueConstraintTranslator() {}

  static ConflictException translate(DataIntegrityViolationException exception) {
    String constraint = constraintNameOf(exception);
    String[] codeAndField =
        constraint == null ? null : CONSTRAINT_TO_CODE_AND_FIELD.get(constraint);

    if (codeAndField == null) {
      // Uma DataIntegrityViolationException não mapeada aqui não é necessariamente duplicidade —
      // pode ser
      // qualquer outra violação de integridade (CHECK, NOT NULL, FK). Loga alto de propósito: virar
      // um 409
      // genérico silenciosamente esconderia um bug real atrás de uma resposta que parece "só
      // concorrência".
      log.error("Violação de integridade não mapeada (constraint={})", constraint, exception);
      return new ConflictException("DUPLICATE_VALUE", "Um dos valores enviados já está em uso.");
    }
    return new ConflictException(
        codeAndField[0],
        "O campo \"" + codeAndField[1] + "\" já está em uso.",
        Map.of("field", codeAndField[1]));
  }

  private static String constraintNameOf(Throwable exception) {
    Throwable cause = exception;
    while (cause != null) {
      // Hibernate já traduz a exceção específica do driver (PSQLException) e extrai o nome da
      // constraint —
      // ler por aqui evita depender em tempo de compilação do driver JDBC, que é dependência de
      // escopo
      // "runtime" de propósito (a aplicação não deveria conhecer o driver).
      if (cause instanceof ConstraintViolationException constraintViolation
          && constraintViolation.getConstraintName() != null) {
        return constraintViolation.getConstraintName();
      }
      cause = cause.getCause();
    }
    return null;
  }
}
