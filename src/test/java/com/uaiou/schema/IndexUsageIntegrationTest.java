package com.uaiou.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-02, critério de aceite 4: a consulta de elegibilidade (entregadores disponíveis numa caixa
 * delimitadora, T-11) usa o índice parcial {@code ix_entregador_disponivel_localizacao}.
 *
 * <p>Com poucas linhas o planejador do PostgreSQL prefere Seq Scan por custo, mesmo com o índice
 * correto — não é sinal de bug do índice, é o comportamento esperado em tabela pequena. Por isso o
 * teste insere volume suficiente (500 disponíveis + 500 indisponíveis) para o índice parcial
 * genuinamente compensar, em vez de confiar só na existência do índice no catálogo.
 */
@Transactional
class IndexUsageIntegrationTest extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void elegibilidadePorProximidadeUsaOIndiceParcialSemVarreduraSequencial() {
    for (int i = 0; i < 500; i++) {
      inserirEntregador(true, -19.9 + (i * 0.0001));
      inserirEntregador(false, -19.9 + (i * 0.0001));
    }

    List<String> plano =
        jdbc.query(
            "explain select usuario_id from entregador "
                + "where disponivel and localizacao_em > now() - interval '5 minutes' "
                + "and lat between -20.0 and -19.8 and long between -44.0 and -43.8",
            (rs, rowNum) -> rs.getString(1));

    String planoTexto = String.join("\n", plano);
    assertThat(planoTexto).contains("ix_entregador_disponivel_localizacao");
    assertThat(planoTexto).doesNotContain("Seq Scan on entregador");
  }

  private void inserirEntregador(boolean disponivel, double lat) {
    UUID usuarioId = UUID.randomUUID();
    jdbc.update(
        "insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) "
            + "values (?, ?, ?, 'hash', 'entregador', 'Fixture', 'ativo')",
        usuarioId,
        "login_" + usuarioId,
        usuarioId + "@teste.dev");
    jdbc.update(
        "insert into entregador (usuario_id, cpf, disponivel, lat, long, localizacao_em) "
            + "values (?, ?, ?, ?, -43.9, now())",
        usuarioId,
        usuarioId.toString().replaceAll("-", "").substring(0, 11),
        disponivel,
        lat);
  }
}
