package com.uaiou.auth.dto;

/**
 * Campos específicos do papel dentro de {@code profile} (api/auth.md). Todos opcionais aqui — a
 * obrigatoriedade por papel (cpf para entregador, cnpj para estabelecimento) é validada no serviço,
 * não por anotação, porque depende do {@code role} irmão no mesmo request.
 */
public record RegisterProfile(
    String cpf, String vehicleType, String vehiclePlate, String cnpj, String businessName) {}
