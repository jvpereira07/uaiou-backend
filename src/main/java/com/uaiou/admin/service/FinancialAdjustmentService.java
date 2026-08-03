package com.uaiou.admin.service;

import com.uaiou.admin.dto.FinancialAdjustmentRequest;
import com.uaiou.admin.dto.FinancialAdjustmentResponse;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.10 — ajuste administrativo de créditos, único caminho para corrigir saldo fora do fluxo
 * normal.
 */
@Service
public class FinancialAdjustmentService {

  private static final String SUPPORTED_TYPE = "credits_adjustment";

  private final EstabelecimentoRepository estabelecimentoRepository;
  private final CreditWalletService creditWalletService;
  private final AuditService auditService;

  public FinancialAdjustmentService(
      EstabelecimentoRepository estabelecimentoRepository,
      CreditWalletService creditWalletService,
      AuditService auditService) {
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.creditWalletService = creditWalletService;
    this.auditService = auditService;
  }

  @Transactional
  public FinancialAdjustmentResponse create(UUID adminId, FinancialAdjustmentRequest request) {
    if (!SUPPORTED_TYPE.equals(request.type())) {
      throw new BadRequestException(
          "UNSUPPORTED_ADJUSTMENT_TYPE", "Só \"" + SUPPORTED_TYPE + "\" é suportado nesta fase.");
    }
    if (request.reference() == null
        || request.reference().type() == null
        || request.reference().type().isBlank()
        || request.reference().id() == null) {
      throw new BusinessRuleException(
          "MISSING_REFERENCE",
          "\"reference\" é obrigatória: todo ajuste nasce de um chamado ou disputa.",
          "RN-13.2");
    }
    estabelecimentoRepository
        .findById(request.targetUserId())
        .orElseThrow(
            () -> new NotFoundException("MERCHANT_NOT_FOUND", "Estabelecimento não encontrado."));

    creditWalletService.ajustar(request.targetUserId(), request.amount());
    auditService.record(
        adminId,
        "ajuste_credito",
        request.reference().type(),
        request.reference().id(),
        request.reason());

    return new FinancialAdjustmentResponse(
        request.type(), request.targetUserId(), request.amount(), request.reason(), Instant.now());
  }
}
