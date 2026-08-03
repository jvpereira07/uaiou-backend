package com.uaiou.delivery.service;

import com.uaiou.delivery.config.DeliveryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra reversível do código de entrega (RN-08.3): o estabelecimento precisa <em>ler</em> o código
 * para repassar ao recebedor, então hash sozinho não serve — mas guardar em claro entregaria todos
 * os códigos ativos a quem comprometesse o banco.
 *
 * <p>AES-GCM: além de cifrar, autentica — um {@code codigo_cifrado} adulterado no banco falha ao
 * decifrar em vez de devolver lixo silenciosamente. O IV é aleatório por código e viaja junto do
 * texto cifrado (é público por desenho; o que não pode repetir é o par IV+chave).
 *
 * <p>A chave configurada é uma <em>passphrase</em>, não 32 bytes crus — derivar por SHA-256 aceita
 * qualquer comprimento sem exigir que a operação gere e transporte bytes binários no ambiente.
 */
@Component
public class DeliveryCodeCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public DeliveryCodeCipher(DeliveryProperties properties) {
    this.key = new SecretKeySpec(derivar(properties.cipherKey()), "AES");
  }

  public String cifrar(String textoClaro) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      random.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] cifrado = cipher.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));

      byte[] saida = new byte[iv.length + cifrado.length];
      System.arraycopy(iv, 0, saida, 0, iv.length);
      System.arraycopy(cifrado, 0, saida, iv.length, cifrado.length);
      return Base64.getEncoder().encodeToString(saida);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao cifrar o código de entrega", e);
    }
  }

  /** Usado por T-15 na leitura auditada pelo estabelecimento e no envio por SMS (T-16). */
  public String decifrar(String base64) {
    try {
      byte[] entrada = Base64.getDecoder().decode(base64);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(entrada, 0, iv, 0, IV_LENGTH_BYTES);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] claro = cipher.doFinal(entrada, IV_LENGTH_BYTES, entrada.length - IV_LENGTH_BYTES);
      return new String(claro, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao decifrar o código de entrega", e);
    }
  }

  private static byte[] derivar(String passphrase) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(passphrase.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }
}
