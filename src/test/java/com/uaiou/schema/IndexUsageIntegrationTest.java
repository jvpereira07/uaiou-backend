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
 * delimitadora, T-11) é satisfazível pelo índice parcial {@code
 * ix_entregador_disponivel_localizacao}.
 *
 * <p>Provar isso por CUSTO do planejador (o {@code EXPLAIN} escolhendo o índice espontaneamente)
 * exigiria um volume de dados de produção — em tabela pequena o Postgres prefere Seq Scan por ser
 * genuinamente mais barato, mesmo com o índice certo (confirmado empiricamente: com 1.000 linhas o
 * planejador ainda escolhe Seq Scan). Isso é comportamento correto do otimizador, não um defeito do
 * índice. O teste então prova o que é de fato uma propriedade do schema — o índice consegue
 * satisfazer a consulta — desligando Seq Scan na sessão ({@code SET LOCAL enable_seqscan = off}) e
 * confirmando que o Postgres o usa sem erro.
 */
@Transactional
class IndexUsageIntegrationTest extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  @Test
  void indiceParcialDeElegibilidadeSatisfazAConsultaDeProximidade() {
    inserirEntregador(true, -19.90);
    inserirEntregador(false, -19.90);

    jdbc.execute("set local enable_seqscan = off");

    List<String> plano =
        jdbc.query(
            "explain select usuario_id from entregador "
                + "where disponivel and localizacao_em > now() - interval '5 minutes' "
                + "and lat between -20.0 and -19.8 and long between -44.0 and -43.8",
            (rs, rowNum) -> rs.getString(1));
    String planoTexto = String.join("\n", plano);

    assertThat(planoTexto).contains("ix_entregador_disponivel_localizacao");

    List<UUID> resultado =
        jdbc.query(
            "select usuario_id from entregador "
                + "where disponivel and localizacao_em > now() - interval '5 minutes' "
                + "and lat between -20.0 and -19.8 and long between -44.0 and -43.8",
            (rs, rowNum) -> (UUID) rs.getObject("usuario_id"));
    assertThat(resultado).hasSize(1);
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
