package com.uaiou.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /admin/financial-adjustments} (api/admin.md). Só {@code type = "credits_adjustment"}
 * está implementado nesta task (T-09) — os outros quatro tipos documentados (fee_refund,
 * balance_credit, bonus_correction, payout_correction) pertencem a saldo/ganhos, ainda não
 * construídos; {@code amount} aqui é sempre delta de créditos (inteiro), não dinheiro, porque é o
 * único tipo suportado.
 *
 * <p>{@code reference} não é {@code @NotNull} aqui de propósito: RF-09.10/RN-13.2 exige 422 (regra
 * de negócio — "todo ajuste nasce de um chamado ou disputa", não payload malformado) quando falta,
 * não o 400 genérico que a validação de bean daria. O serviço valida isso manualmente.
 */
public record FinancialAdjustmentRequest(
    @NotBlank String type,
    @NotNull UUID targetUserId,
    @NotNull Integer amount,
    @NotBlank String reason,
    ReferenceRef reference) {

  public record ReferenceRef(String type, UUID id) {}
}
