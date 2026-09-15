package com.uaiou.users.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.uaiou.users.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;

/**
 * Um único formato para os dois papéis (RF-04.1) — os campos que não se aplicam ao {@code role} do
 * usuário ficam nulos e somem do JSON ({@code spring.jackson.default-property-inclusion:
 * non_null}), em vez de precisar de dois DTOs de resposta espelhando o mesmo recurso.
 */
public record MeProfile(
    // Entregador — verificados (RF-04.3)
    String cpf,
    String vehicleType,
    String vehiclePlate,
    // Entregador — presença (T-10). Nulos para estabelecimento e somem do JSON.
    Boolean available,
    CourierLocation location,
    // Entregador — materializados (RF-04.7)
    Integer completedDeliveries,
    // Estabelecimento — verificado (RF-04.3)
    String cnpj,
    // Estabelecimento — informativo/livre
    String businessName,
    String logoObjectKey,
    Address address,
    // Comum aos dois papéis
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal score,
    // Entregador — livre
    List<PaymentMethod> paymentMethods) {}
