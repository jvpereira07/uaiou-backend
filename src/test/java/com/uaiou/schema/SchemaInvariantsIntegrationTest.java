package com.uaiou.schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-02, critério de aceite 3: cada invariante é provado <b>pelo banco</b>, não pela aplicação —
 * inserts diretos via JDBC (nenhuma entidade JPA existe ainda; é intencional, essa é a fronteira
 * desta task).
 *
 * <p>{@code @Transactional} na classe: cada teste roda na própria transação, revertida
 * automaticamente ao fim — a violação esperada aborta a transação do banco, mas isso não afeta os
 * demais testes porque cada um começa do zero.
 */
@Transactional
class SchemaInvariantsIntegrationTest extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbc;

  // --- fixtures mínimas
  // -----------------------------------------------------------------------------

  private UUID novoUsuario(String tipo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) "
            + "values (?, ?, ?, 'hash', ?, 'Fixture', 'ativo')",
        id,
        "login_" + id,
        id + "@teste.dev",
        tipo);
    return id;
  }

  private UUID novoEstabelecimento() {
    UUID id = novoUsuario("estabelecimento");
    jdbc.update(
        "insert into estabelecimento (usuario_id, cnpj, nome_fantasia) values (?, ?, 'Fixture Ltda')",
        id,
        id.toString().replaceAll("-", "").substring(0, 14));
    return id;
  }

  private UUID novoEntregador() {
    UUID id = novoUsuario("entregador");
    jdbc.update(
        "insert into entregador (usuario_id, cpf) values (?, ?)",
        id,
        id.toString().replaceAll("-", "").substring(0, 11));
    return id;
  }

  private UUID novoPedido(UUID estabelecimentoId, UUID entregadorId, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into pedido (id, numero, estabelecimento_id, entregador_id, status, frete_proposto, "
            + "frete_final, creditos_consumidos, dest_bairro, dest_rua, dest_numero, dest_lat, dest_long, "
            + "recebedor_nome) values (?, ?, ?, ?, ?, 6.00, ?, 1, 'Centro', 'Rua X', '1', -19.9, -43.9, 'Fulano')",
        id,
        "P-" + id,
        estabelecimentoId,
        entregadorId,
        status,
        entregadorId == null ? null : 6.00);
    return id;
  }

  // --- RN-07.1: bloqueio único por par
  // ---------------------------------------------------------------

  @Test
  void bloqueioDuplicadoParaOMesmoParViolaUnicidade() {
    UUID estabelecimento = novoEstabelecimento();
    UUID entregador = novoEntregador();
    jdbc.update(
        "insert into bloqueio (id, estabelecimento_id, entregador_id) values (?, ?, ?)",
        UUID.randomUUID(),
        estabelecimento,
        entregador);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into bloqueio (id, estabelecimento_id, entregador_id) values (?, ?, ?)",
                    UUID.randomUUID(),
                    estabelecimento,
                    entregador))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- RN-03: uma avaliação por autor por pedido
  // ------------------------------------------------------

  @Test
  void segundaAvaliacaoDoMesmoAutorNoMesmoPedidoViolaUnicidade() {
    UUID estabelecimento = novoEstabelecimento();
    UUID entregador = novoEntregador();
    UUID pedido = novoPedido(estabelecimento, entregador, "finalizado");
    jdbc.update(
        "insert into avaliacao (id, pedido_id, autor_id, alvo_id, nota) values (?, ?, ?, ?, 5)",
        UUID.randomUUID(),
        pedido,
        estabelecimento,
        entregador);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into avaliacao (id, pedido_id, autor_id, alvo_id, nota) values (?, ?, ?, ?, 4)",
                    UUID.randomUUID(),
                    pedido,
                    estabelecimento,
                    entregador))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void avaliacaoComAutorIgualAoAlvoViolaCheck() {
    UUID estabelecimento = novoEstabelecimento();
    UUID entregador = novoEntregador();
    UUID pedido = novoPedido(estabelecimento, entregador, "finalizado");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into avaliacao (id, pedido_id, autor_id, alvo_id, nota) values (?, ?, ?, ?, 5)",
                    UUID.randomUUID(),
                    pedido,
                    estabelecimento,
                    estabelecimento))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- 1:1 pedido-otp
  // ----------------------------------------------------------------------------------

  @Test
  void doisOtpParaOMesmoPedidoViolaUnicidade() {
    UUID estabelecimento = novoEstabelecimento();
    UUID entregador = novoEntregador();
    UUID pedido = novoPedido(estabelecimento, entregador, "aceito");
    jdbc.update(
        "insert into otp (id, pedido_id, codigo_hash, codigo_cifrado, expira_em) values (?, ?, 'h1', 'c1', now() + interval '1 hour')",
        UUID.randomUUID(),
        pedido);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into otp (id, pedido_id, codigo_hash, codigo_cifrado, expira_em) values (?, ?, 'h2', 'c2', now() + interval '1 hour')",
                    UUID.randomUUID(),
                    pedido))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- RF-02.4: saldo de créditos nunca negativo
  // -------------------------------------------------------

  @Test
  void carteiraDeCreditosComSaldoNegativoViolaCheck() {
    UUID estabelecimento = novoEstabelecimento();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into carteira_creditos (estabelecimento_id, saldo_creditos) values (?, -1)",
                    estabelecimento))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- RN-01.2: pedido aceito exige entregador e frete final
  // --------------------------------------------

  @Test
  void pedidoAceitoSemEntregadorViolaCheck() {
    UUID estabelecimento = novoEstabelecimento();

    assertThatThrownBy(() -> novoPedido(estabelecimento, null, "aceito"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- RN-10.2: evidência contestável exige foto
  // ---------------------------------------------------------

  @Test
  void evidenciaContestavelSemUploadViolaCheck() {
    UUID estabelecimento = novoEstabelecimento();
    UUID entregador = novoEntregador();
    UUID pedido = novoPedido(estabelecimento, entregador, "finalizado_contestavel");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidencia_entrega (id, pedido_id, tipo_finalizacao, lat, long) values (?, ?, 'contestavel', -19.9, -43.9)",
                    UUID.randomUUID(),
                    pedido))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // --- Coerência de credencial: usuário precisa de senha OU google
  // --------------------------------------

  @Test
  void usuarioSemSenhaESemGoogleViolaCheck() {
    UUID id = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into usuario (id, login, email, tipo, nome_exibicao) values (?, ?, ?, 'entregador', 'Sem Credencial')",
                    id,
                    "login_" + id,
                    id + "@teste.dev"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
