package com.uaiou.uploads;

/**
 * Vocabulário do propósito do upload, em inglês — o que trafega em JSON (api/uploads.md). A coluna
 * {@code upload.purpose} no banco é em português; a tradução é feita por {@link PurposeConverter}.
 *
 * <p>{@code CNPJ_DOCUMENT} não está documentado em api/uploads.md (a lista lá tem só cinco
 * valores), mas o banco (V2__upload.sql, {@code ck_upload_purpose}) já previa {@code
 * documento_cnpj} desde o T-02 — mesma reconciliação já usada em T-03 para o cadastro de
 * estabelecimento ({@code RegistrationService.MERCHANT_REQUIRED_DOCUMENTS}) e em T-04 (edição
 * verificada de CNPJ).
 */
public enum Purpose {
  IDENTITY_DOCUMENT,
  DRIVER_LICENSE,
  VEHICLE_DOCUMENT,
  MERCHANT_LOGO,
  DELIVERY_PROOF,
  CNPJ_DOCUMENT
}
