package com.uaiou.uploads;

import java.util.Set;

/**
 * RF-05.2: {@code purpose} não é rótulo livre — define MIME aceito e tamanho máximo. Todos os
 * propósitos usam a mesma política por ora (uniforme em imagem/jpeg+png, 5 MB — o único número
 * concreto que api/uploads.md documenta, no exemplo de resposta de {@code POST /uploads}); o método
 * existe separado por propósito para o dia em que um deles precisar de regra própria (ex.: aceitar
 * PDF em documento de cadastro), sem precisar tocar em quem chama.
 */
public final class PurposePolicy {

  private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
  private static final long DEFAULT_MAX_SIZE_BYTES = 5L * 1024 * 1024;

  private PurposePolicy() {}

  public record Policy(Set<String> allowedContentTypes, long maxSizeBytes) {

    public boolean allows(String contentType) {
      return allowedContentTypes.contains(contentType);
    }
  }

  public static Policy of(Purpose purpose) {
    return new Policy(IMAGE_CONTENT_TYPES, DEFAULT_MAX_SIZE_BYTES);
  }
}
