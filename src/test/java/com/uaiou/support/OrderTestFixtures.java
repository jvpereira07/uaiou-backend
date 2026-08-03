package com.uaiou.support;

import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Atalhos de estado para os testes de T-11 que dependem de rotas ainda não construídas: dar plano e
 * crédito ao estabelecimento passa por T-09 (existe), mas bloquear entregador é de T-12 (não
 * existe), então o bloqueio é inserido direto via SQL.
 */
@Component
public class OrderTestFixtures {

  private final JdbcTemplate jdbcTemplate;

  public OrderTestFixtures(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** T-12 ainda não tem rota de bloqueio; o motor de elegibilidade (RF-11.5) já lê a tabela. */
  public void bloquear(UUID estabelecimentoId, UUID entregadorId, String motivo) {
    jdbcTemplate.update(
        "insert into bloqueio (id, estabelecimento_id, entregador_id, motivo) values (?, ?, ?, ?)",
        UuidV7.next(),
        estabelecimentoId,
        entregadorId,
        motivo);
  }

  /** Crédito direto na carteira — evita depender do catálogo de planos em teste de pedido. */
  public void darCreditos(UUID estabelecimentoId, int quantidade) {
    jdbcTemplate.update(
        "insert into carteira_creditos (estabelecimento_id, saldo_creditos) values (?, ?)"
            + " on conflict (estabelecimento_id) do update set saldo_creditos = excluded.saldo_creditos",
        estabelecimentoId,
        quantidade);
  }

  public int saldoDe(UUID estabelecimentoId) {
    Integer saldo =
        jdbcTemplate.queryForObject(
            "select coalesce((select saldo_creditos from carteira_creditos where estabelecimento_id = ?), 0)",
            Integer.class,
            estabelecimentoId);
    return saldo == null ? 0 : saldo;
  }

  public int contarPedidosDe(UUID estabelecimentoId) {
    Integer total =
        jdbcTemplate.queryForObject(
            "select count(*) from pedido where estabelecimento_id = ?",
            Integer.class,
            estabelecimentoId);
    return total == null ? 0 : total;
  }

  /** Status COMO ESTÁ NO BANCO — distinto do que a resposta da criação ecoou de memória. */
  public String statusPersistidoDe(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select status from pedido where id = ?", String.class, pedidoId);
  }

  public Money freteDe(UUID pedidoId) {
    return Money.of(
        jdbcTemplate.queryForObject(
            "select frete_proposto::text from pedido where id = ?", String.class, pedidoId));
  }
}
