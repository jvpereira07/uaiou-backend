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
      @Size(max = 80) String rotulo,
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

  /** Resposta da captura: a tela que o visitante mostra no balcão. */
  public record CapturaResponse(
      UUID id, UUID pedidoId, String recompensa, Instant capturadoEm, Instant entregueEm) {}

  /** Visão do operador: quem pegou o quê e se já retirou. */
  public record CapturaAdminResponse(
      UUID id,
      UUID pedidoId,
      UUID jogadorId,
      String jogadorNome,
      String jogadorUsername,
      Instant capturadoEm,
      Instant entregueEm) {}

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
