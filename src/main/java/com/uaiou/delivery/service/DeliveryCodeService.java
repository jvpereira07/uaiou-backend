package com.uaiou.delivery.service;

import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.shared.id.UuidV7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-13.4 / critério de aceite 8 — o código de entrega nasce junto do aceite, dentro da mesma
 * transação: pedido aceito sem código seria uma entrega impossível de finalizar.
 *
 * <p>O valor claro é devolvido <strong>uma única vez</strong>, para quem chamou despachar pelos
 * canais (T-15/T-16), e nunca é persistido. O que fica no banco é o hash (comparação) e a forma
 * cifrada (leitura auditada pelo estabelecimento).
 *
 * <p>SHA-256 e não BCrypt para o hash: a finalização compara o código a cada tentativa, e um KDF
 * lento por desenho não protegeria mais aqui — o espaço é de 6 dígitos, então quem tem o hash e
 * quer forçá-lo consegue de qualquer jeito. O que protege é o limite de tentativas e a expiração
 * (RN-08.7, T-15), não o custo do hash.
 */
@Service
public class DeliveryCodeService {

  /**
   * Tamanho do código. Público porque o <strong>cliente não pode fixar este número</strong>: a tela
   * do entregador tinha 4 escrito à mão e recusava o código real de 6 antes mesmo de enviá-lo. O
   * estado da entrega (T-15) devolve este valor, e mudar aqui passa a bastar.
   */
  public static final int DIGITOS = 6;

  private final OtpRepository otpRepository;
  private final DeliveryCodeCipher cipher;
  private final DeliveryProperties properties;
  private final SecureRandom random = new SecureRandom();

  public DeliveryCodeService(
      OtpRepository otpRepository, DeliveryCodeCipher cipher, DeliveryProperties properties) {
    this.otpRepository = otpRepository;
    this.cipher = cipher;
    this.properties = properties;
  }

  /**
   * @return o código em claro, só para despacho imediato — não volte a persisti-lo.
   */
  @Transactional
  public String gerarPara(UUID pedidoId) {
    String codigo = sortearCodigo();
    otpRepository.save(
        new Otp(
            UuidV7.next(),
            pedidoId,
            hash(codigo),
            cipher.cifrar(codigo),
            Instant.now().plus(properties.codeTtl())));
    return codigo;
  }

  public static String hash(String codigo) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }

  /** Exposto só para o teste unitário do gerador — não use em produção. */
  String sortearCodigoParaTeste() {
    return sortearCodigo();
  }

  private String sortearCodigo() {
    // SecureRandom, não Random: o código é credencial de entrega — previsível seria o mesmo que
    // não existir.
    int valor = random.nextInt((int) Math.pow(10, DIGITOS));
    return String.format("%0" + DIGITOS + "d", valor);
  }
}
