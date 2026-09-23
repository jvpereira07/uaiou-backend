package com.uaiou.feira.dto;

import com.uaiou.auth.dto.SessionResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contratos do modo feira (docs/feira/01-fluxos.md). Num arquivo só porque são poucos, pequenos e
 * mudam juntos — o produto separa por fluxo porque lá cada fluxo tem vida própria.
 */
public final class FeiraDtos {

  private FeiraDtos() {}

  /** Entrada do jogador: nome e nome de usuário, nada mais. */
  public record EntrarRequest(
      @NotBlank @Size(max = 120) String nome, @NotBlank @Size(max = 20) String username) {}

  /**
   * A sessão vem junto da entrada: não existe tela de login separada, porque não existe segredo a
   * provar. {@code novoJogador} distingue primeira entrada de reentrada pelo mesmo nome.
   */
  public record EntrarResponse(SessionResponse sessao, boolean novoJogador) {}

  /** Publicação pelo admin: onde, o que se ganha, e quantos existem. */
  public record CriarPedidoRequest(
      @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
      @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
      @NotBlank @Size(max = 80) String rotulo,
      @NotBlank @Size(max = 60) String recompensa,
      @NotNull @Min(1) @Max(999) Integer limite) {}

  /**
   * O que o jogador vê. {@code capturado} é por jogador: é o que faz o botão virar "você já pegou"
   * sem o app precisar cruzar listas.
   */
  public record PedidoResponse(
      UUID id,
      String rotulo,
      BigDecimal lat,
      BigDecimal longitude,
      String recompensa,
      int restantes,
      boolean encerrado,
      boolean capturado) {}

  /**
   * Resposta da captura. {@code pedidoId} é o modelo; {@code pedidoGeradoId} é o pedido do jogador,
   * e é ele que o app abre para executar a entrega.
   */
  public record CapturaResponse(
      UUID id,
      UUID pedidoId,
      UUID pedidoGeradoId,
      String recompensa,
      Instant capturadoEm,
      Instant entregueEm) {}

  /** Visão do operador: quem pegou o quê, em que ponto do fluxo está e se já retirou. */
  public record CapturaAdminResponse(
      UUID id,
      UUID pedidoId,
      UUID pedidoGeradoId,
      UUID jogadorId,
      String jogadorNome,
      String jogadorUsername,
      String situacao,
      boolean aguardandoColeta,
      Instant capturadoEm,
      Instant entregueEm) {}

  /**
   * Ponto do estande UaiOu. É de lá que o jogador retira, então ele precisa aparecer no mapa do app
   * — e a posição da mesa só se sabe no dia, por isso é editável no painel.
   */
  public record EstandeRequest(
      @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
      @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
      @Size(max = 200) String descricao) {}

  /** {@code descricao} complementa o pino: num salão, o mapa sozinho não acha a mesa. */
  public record EstandeResponse(BigDecimal lat, BigDecimal longitude, String descricao) {}

  /** Visão do operador sobre o pedido, com o estoque à vista. */
  public record PedidoAdminResponse(
      UUID id,
      String numero,
      String rotulo,
      BigDecimal lat,
      BigDecimal longitude,
      String recompensa,
      int limite,
      int capturas,
      int restantes,
      Instant encerradoEm,
      Instant criadoEm) {}
}
