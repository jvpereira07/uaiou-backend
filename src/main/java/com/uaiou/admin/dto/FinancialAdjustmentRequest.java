package com.uaiou.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code POST /admin/financial-adjustments} (api/admin.md). {@code credits_adjustment} (T-09) e
 * {@code payout_correction} (T-18/RF-18.7) estão implementados; os outros três tipos documentados
 * (fee_refund, balance_credit, bonus_correction) pertencem a saldo/ganhos, ainda não construídos.
 *
 * <p>{@code amount} continua {@code Integer} pelos dois tipos, com significado diferente por
 * desenho: delta de créditos em {@code credits_adjustment} (decisão original de T-09, mantida para
 * não alterar um contrato já verificado), e <strong>centavos</strong> em {@code payout_correction}
 * — evita trocar o tipo do campo por um recurso periférico; {@link
 * com.uaiou.admin.service.FinancialAdjustmentService} converte para {@link
 * com.uaiou.shared.money.Money} antes de gravar.
 *
 * <p>{@code lancamentoId} é usado só por {@code payout_correction} — identifica qual {@code
 * lancamento_frete} está sendo corrigido, já que {@code targetUserId} sozinho (o entregador) não
 * distingue entre os lançamentos dele.
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
    ReferenceRef reference,
    UUID lancamentoId) {

  public record ReferenceRef(String type, UUID id) {}
}
