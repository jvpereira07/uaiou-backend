package com.uaiou.admin.service;

import com.uaiou.admin.dto.FinancialAdjustmentRequest;
import com.uaiou.admin.dto.FinancialAdjustmentResponse;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.delivery.entity.AjusteLancamentoFrete;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.AjusteLancamentoFreteRepository;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.10/RF-18.7 — ajuste administrativo, único caminho para corrigir saldo ou lançamento fora do
 * fluxo normal.
 */
@Service
public class FinancialAdjustmentService {

  private static final Set<String> SUPPORTED_TYPES =
      Set.of("credits_adjustment", "payout_correction");
  private static final String CREDITS_TYPE = "credits_adjustment";
  private static final String PAYOUT_TYPE = "payout_correction";

  private final EstabelecimentoRepository estabelecimentoRepository;
  private final CreditWalletService creditWalletService;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final AjusteLancamentoFreteRepository ajusteLancamentoFreteRepository;
  private final AuditService auditService;

  public FinancialAdjustmentService(
      EstabelecimentoRepository estabelecimentoRepository,
      CreditWalletService creditWalletService,
      LancamentoFreteRepository lancamentoFreteRepository,
      AjusteLancamentoFreteRepository ajusteLancamentoFreteRepository,
      AuditService auditService) {
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.creditWalletService = creditWalletService;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.ajusteLancamentoFreteRepository = ajusteLancamentoFreteRepository;
    this.auditService = auditService;
  }

  @Transactional
  public FinancialAdjustmentResponse create(UUID adminId, FinancialAdjustmentRequest request) {
    if (!SUPPORTED_TYPES.contains(request.type())) {
      throw new BadRequestException(
          "UNSUPPORTED_ADJUSTMENT_TYPE", "Só " + SUPPORTED_TYPES + " são suportados nesta fase.");
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

    if (CREDITS_TYPE.equals(request.type())) {
      return ajustarCreditos(adminId, request);
    }
    return corrigirLancamento(adminId, request);
  }

  private FinancialAdjustmentResponse ajustarCreditos(
      UUID adminId, FinancialAdjustmentRequest request) {
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

  /**
   * RF-18.7 — o lançamento original NUNCA é reescrito (RN-13.1): a correção é uma linha nova em
   * {@code ajuste_lancamento_frete}, vinculada por {@code lancamentoId}. {@code amount} chega em
   * <strong>centavos</strong> (ver javadoc de {@link FinancialAdjustmentRequest}) para não mudar o
   * tipo do campo compartilhado com {@code credits_adjustment}.
   */
  private FinancialAdjustmentResponse corrigirLancamento(
      UUID adminId, FinancialAdjustmentRequest request) {
    if (request.lancamentoId() == null) {
      throw new BusinessRuleException(
          "MISSING_LANCAMENTO_ID",
          "\"lancamentoId\" é obrigatório para corrigir um lançamento do livro-razão.",
          "RN-13.1");
    }
    LancamentoFrete lancamento =
        lancamentoFreteRepository
            .findById(request.lancamentoId())
            .filter(candidato -> candidato.pertenceAoEntregador(request.targetUserId()))
            .orElseThrow(
                () -> new NotFoundException("LANCAMENTO_NOT_FOUND", "Lançamento não encontrado."));

    Money novoValor = Money.of(BigDecimal.valueOf(request.amount(), 2));
    ajusteLancamentoFreteRepository.save(
        new AjusteLancamentoFrete(
            UuidV7.next(),
            lancamento.getId(),
            novoValor,
            request.reason(),
            request.reference().type(),
            request.reference().id(),
            adminId));
    auditService.record(
        adminId,
        "ajuste_lancamento",
        request.reference().type(),
        request.reference().id(),
        request.reason());

    return new FinancialAdjustmentResponse(
        request.type(), request.targetUserId(), request.amount(), request.reason(), Instant.now());
  }
}
