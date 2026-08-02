package com.uaiou.users.dto;

/** Endereço do estabelecimento — campo livre (RF-04.3, T-04), sempre trocado como um grupo. */
public record Address(String bairro, String rua, String numero, String cidade, String cep) {}
