package com.uaiou.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.delivery.config.DeliveryProperties;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Teste unitário (sem Spring, sem banco) da cifra do código de entrega — RN-08.3. É a peça de
 * criptografia de T-13, e o que ela promete precisa valer por si só: ida e volta fiel, texto
 * cifrado que não revela o claro, e detecção de adulteração.
 */
class DeliveryCodeCipherTest {

  private final DeliveryCodeCipher cipher =
      new DeliveryCodeCipher(propriedades("chave-de-teste-do-codigo"));

  @Test
  void cifrarEDecifrarDevolveOMesmoCodigo() {
    String codigo = "042317";

    assertThat(cipher.decifrar(cipher.cifrar(codigo))).isEqualTo(codigo);
  }

  @Test
  void oTextoCifradoNaoContemOCodigoEmClaro() {
    String codigo = "042317";

    String cifrado = cipher.cifrar(codigo);

    assertThat(cifrado).doesNotContain(codigo);
  }

  /**
   * IV aleatório por chamada: cifrar o mesmo código duas vezes precisa dar resultados diferentes,
   * senão o texto cifrado viraria um identificador estável do código — quem visse dois pedidos com
   * o mesmo valor saberia que o código é o mesmo.
   */
  @Test
  void cifrarOMesmoCodigoDuasVezesProduzResultadosDiferentes() {
    String codigo = "042317";

    assertThat(cipher.cifrar(codigo)).isNotEqualTo(cipher.cifrar(codigo));
  }

  /** AES-GCM autentica: um valor adulterado no banco falha em vez de devolver lixo em silêncio. */
  @Test
  void umTextoCifradoAdulteradoFalhaAoDecifrar() {
    String cifrado = cipher.cifrar("042317");
    String adulterado =
        cifrado.substring(0, cifrado.length() - 4) + (cifrado.endsWith("AAAA") ? "BBBB" : "AAAA");

    assertThatThrownBy(() -> cipher.decifrar(adulterado)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void chaveDiferenteNaoDecifra() {
    DeliveryCodeCipher outra = new DeliveryCodeCipher(propriedades("outra-chave-completamente"));

    String cifrado = cipher.cifrar("042317");

    assertThatThrownBy(() -> outra.decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void oHashEEstavelEDistintoDoCodigo() {
    assertThat(DeliveryCodeService.hash("042317"))
        .startsWith("sha256:")
        .isEqualTo(DeliveryCodeService.hash("042317"))
        .isNotEqualTo(DeliveryCodeService.hash("042318"));
  }

  /**
   * Sanidade do gerador: 6 dígitos com zero à esquerda preservado (um código "042317" que virasse
   * "42317" não bateria na comparação da finalização) e sem repetição óbvia.
   */
  @Test
  void osCodigosGeradosTemSeisDigitosESaoVariados() {
    Set<String> vistos = new HashSet<>();
    for (int i = 0; i < 200; i++) {
      String codigo = new DeliveryCodeService(null, cipher, null).sortearCodigoParaTeste();
      assertThat(codigo).hasSize(6).matches("\\d{6}");
      vistos.add(codigo);
    }
    assertThat(vistos).hasSizeGreaterThan(100);
  }

  private static DeliveryProperties propriedades(String cipherKey) {
    return new DeliveryProperties(
        cipherKey,
        Duration.ofHours(24),
        150,
        3,
        300,
        Duration.ofMinutes(30),
        2,
        10,
        Duration.ofDays(1));
  }
}
