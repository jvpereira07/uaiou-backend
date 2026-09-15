package com.uaiou.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Todo campo é opcional — PATCH aplica só o que veio, nunca o corpo inteiro (RF-04.3). */
public record PatchMeRequest(
    @Size(max = 120) String displayName,
    @Size(max = 20) String telefone,
    @Valid PatchMeProfile profile) {}
