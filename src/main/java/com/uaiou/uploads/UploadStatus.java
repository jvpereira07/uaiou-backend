package com.uaiou.uploads;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Ao contrário de {@link Purpose}/status de usuário, os valores de banco ({@code
 * awaiting_upload}/{@code ready}, V2__upload.sql) já são a forma minúscula-com-underscore destes
 * nomes — {@link #toJson()} e {@link UploadStatusConverter} usam essa transformação mecânica em vez
 * de um switch exaustivo, porque a correspondência é garantida por construção, não por uma tabela
 * de tradução que possa ficar incompleta.
 */
public enum UploadStatus {
  AWAITING_UPLOAD,
  READY;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static UploadStatus fromJson(String value) {
    return UploadStatus.valueOf(value.toUpperCase());
  }
}
