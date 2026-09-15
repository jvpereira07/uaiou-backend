package com.uaiou.users.dto;

import com.uaiou.users.PaymentMethod;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Campos particionados por sensibilidade (RF-04.3): os "verificados" (cpf, veículo, cnpj) só se
 * aplicam quando acompanhados do upload que os comprova — sem o upload correspondente, a edição é
 * rejeitada (400), porque não há como abrir moderação sem prova nenhuma anexada.
 */
public record PatchMeProfile(
    // Entregador — verificado
    String cpf,
    UUID identityUploadId,
    String vehicleType,
    String vehiclePlate,
    UUID vehicleUploadId,
    // Estabelecimento — verificado
    String cnpj,
    UUID cnpjUploadId,
    // Estabelecimento — livre
    String logoObjectKey,
    // Limites espelham as colunas de V1__identidade.sql — acima disso o banco recusaria com 500.
    @Size(max = 80) String bairro,
    @Size(max = 120) String rua,
    @Size(max = 10) String numero,
    @Size(max = 80) String cidade,
    @Size(max = 9) String cep,
    BigDecimal lat,
    BigDecimal lng,
    // Entregador — livre. Substitui o conjunto inteiro; [] limpa, ausente não mexe.
    List<PaymentMethod> paymentMethods,
    // Comum — livre. Upload confirmado: COURIER_PHOTO para entregador, MERCHANT_LOGO para
    // estabelecimento. O vínculo é pelo upload, não pela chave, para que ninguém aponte o perfil
    // para um objeto que não enviou.
    UUID photoUploadId) {

  /** Formato anterior à foto de perfil — mantido para quem não mexe nela. */
  public PatchMeProfile(
      String cpf,
      UUID identityUploadId,
      String vehicleType,
      String vehiclePlate,
      UUID vehicleUploadId,
      String cnpj,
      UUID cnpjUploadId,
      String logoObjectKey,
      String bairro,
      String rua,
      String numero,
      String cidade,
      String cep,
      BigDecimal lat,
      BigDecimal lng,
      List<PaymentMethod> paymentMethods) {
    this(
        cpf,
        identityUploadId,
        vehicleType,
        vehiclePlate,
        vehicleUploadId,
        cnpj,
        cnpjUploadId,
        logoObjectKey,
        bairro,
        rua,
        numero,
        cidade,
        cep,
        lat,
        lng,
        paymentMethods,
        null);
  }

  /** Formato anterior à forma de pagamento — mantido para quem não mexe nela. */
  public PatchMeProfile(
      String cpf,
      UUID identityUploadId,
      String vehicleType,
      String vehiclePlate,
      UUID vehicleUploadId,
      String cnpj,
      UUID cnpjUploadId,
      String logoObjectKey,
      String bairro,
      String rua,
      String numero,
      String cidade,
      String cep,
      BigDecimal lat,
      BigDecimal lng) {
    this(
        cpf,
        identityUploadId,
        vehicleType,
        vehiclePlate,
        vehicleUploadId,
        cnpj,
        cnpjUploadId,
        logoObjectKey,
        bairro,
        rua,
        numero,
        cidade,
        cep,
        lat,
        lng,
        null,
        null);
  }
}
