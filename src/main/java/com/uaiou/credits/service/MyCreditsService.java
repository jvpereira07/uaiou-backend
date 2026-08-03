package com.uaiou.credits.service;

import com.uaiou.credits.SubscriptionStatus;
import com.uaiou.credits.dto.CreditTransactionSummary;
import com.uaiou.credits.dto.MyCreditsResponse;
import com.uaiou.credits.dto.SubscriptionSummary;
import com.uaiou.credits.entity.Assinatura;
import com.uaiou.credits.entity.CarteiraCreditos;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.entity.TransacaoCredito;
import com.uaiou.credits.repository.AssinaturaRepository;
import com.uaiou.credits.repository.CarteiraCreditosRepository;
import com.uaiou.credits.repository.PlanoRepository;
import com.uaiou.credits.repository.TransacaoCreditoRepository;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-09.8/RF-09.9 — visão do próprio estabelecimento sobre seus créditos. */
@Service
public class MyCreditsService {

  private final CarteiraCreditosRepository carteiraCreditosRepository;
  private final AssinaturaRepository assinaturaRepository;
  private final PlanoRepository planoRepository;
  private final TransacaoCreditoRepository transacaoCreditoRepository;
  private final CreditReportingService creditReportingService;

  public MyCreditsService(
      CarteiraCreditosRepository carteiraCreditosRepository,
      AssinaturaRepository assinaturaRepository,
      PlanoRepository planoRepository,
      TransacaoCreditoRepository transacaoCreditoRepository,
      CreditReportingService creditReportingService) {
    this.carteiraCreditosRepository = carteiraCreditosRepository;
    this.assinaturaRepository = assinaturaRepository;
    this.planoRepository = planoRepository;
    this.transacaoCreditoRepository = transacaoCreditoRepository;
    this.creditReportingService = creditReportingService;
  }

  @Transactional(readOnly = true)
  public MyCreditsResponse getCredits(UUID estabelecimentoId) {
    int saldo =
        carteiraCreditosRepository
            .findById(estabelecimentoId)
            .map(CarteiraCreditos::getSaldoCreditos)
            .orElse(0);

    Optional<Assinatura> assinatura =
        assinaturaRepository.findByEstabelecimentoIdAndStatus(
            estabelecimentoId, SubscriptionStatus.ACTIVE);
    SubscriptionSummary subscription =
        assinatura
            .map(
                a -> {
                  Plano plano =
                      planoRepository
                          .findById(a.getPlanoId())
                          .orElseThrow(
                              () ->
                                  new IllegalStateException(
                                      "Assinatura "
                                          + a.getId()
                                          + " referencia plano inexistente."));
                  return creditReportingService.buildSummary(estabelecimentoId, a, plano);
                })
            .orElse(null);

    return new MyCreditsResponse(saldo, subscription, buildLinks());
  }

  @Transactional(readOnly = true)
  public PageResponse<CreditTransactionSummary> listTransactions(
      UUID estabelecimentoId, PagingRequest paging, String baseUri) {
    Page<TransacaoCredito> page =
        transacaoCreditoRepository.findByEstabelecimentoIdOrderByCriadoEmDesc(
            estabelecimentoId, PageRequest.of(paging.page() - 1, paging.perPage()));
    return Paginator.paginate(page.map(this::toSummary), paging, baseUri);
  }

  private CreditTransactionSummary toSummary(TransacaoCredito transacao) {
    return new CreditTransactionSummary(
        transacao.getId(),
        transacao.getTipo(),
        transacao.getQuantidade(),
        transacao.getPedidoId(),
        transacao.getCriadoEm());
  }

  private Map<String, LinkRef> buildLinks() {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("transactions", LinkRef.get("/api/v1/me/credits/transactions"));
    return links;
  }
}
