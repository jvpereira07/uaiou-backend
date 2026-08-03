package com.uaiou.admin.service;

import com.uaiou.credits.SubscriptionStatus;
import com.uaiou.credits.dto.SubscriptionSummary;
import com.uaiou.credits.entity.Assinatura;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.repository.AssinaturaRepository;
import com.uaiou.credits.service.CreditReportingService;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.credits.service.PlanoService;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.2/RF-09.3 — orquestra a atribuição/troca de plano: mecânica pura da carteira fica em {@code
 * com.uaiou.credits} (reusável), a auditoria fica aqui porque é {@code com.uaiou.admin} quem
 * conhece {@link AuditService} — mesma direção de dependência já usada em T-07
 * (RegistrationReviewService toca repositórios de {@code users} diretamente e audita na mesma
 * transação).
 */
@Service
public class MerchantPlanService {

  private final EstabelecimentoRepository estabelecimentoRepository;
  private final PlanoService planoService;
  private final AssinaturaRepository assinaturaRepository;
  private final CreditWalletService creditWalletService;
  private final CreditReportingService creditReportingService;
  private final AuditService auditService;

  public MerchantPlanService(
      EstabelecimentoRepository estabelecimentoRepository,
      PlanoService planoService,
      AssinaturaRepository assinaturaRepository,
      CreditWalletService creditWalletService,
      CreditReportingService creditReportingService,
      AuditService auditService) {
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.planoService = planoService;
    this.assinaturaRepository = assinaturaRepository;
    this.creditWalletService = creditWalletService;
    this.creditReportingService = creditReportingService;
    this.auditService = auditService;
  }

  @Transactional
  public SubscriptionSummary assign(UUID adminId, UUID estabelecimentoId, UUID novoPlanoId) {
    estabelecimentoRepository
        .findById(estabelecimentoId)
        .orElseThrow(
            () -> new NotFoundException("MERCHANT_NOT_FOUND", "Estabelecimento não encontrado."));
    Plano novoPlano = planoService.requirePlano(novoPlanoId);

    Optional<Assinatura> existente =
        assinaturaRepository.findByEstabelecimentoIdAndStatus(
            estabelecimentoId, SubscriptionStatus.ACTIVE);

    Assinatura assinatura;
    if (existente.isEmpty()) {
      assinatura = new Assinatura(UuidV7.next(), estabelecimentoId, novoPlanoId);
      assinaturaRepository.save(assinatura);
      creditWalletService.creditarCota(
          estabelecimentoId, novoPlano.getCotaMensalCreditos(), assinatura.getId());
      auditService.record(
          adminId,
          "atribuir_plano",
          "estabelecimento",
          estabelecimentoId,
          "Plano \"" + novoPlano.getNome() + "\" atribuído.");
    } else {
      assinatura = existente.get();
      if (assinatura.getPlanoId().equals(novoPlanoId)) {
        auditService.record(
            adminId,
            "atribuir_plano",
            "estabelecimento",
            estabelecimentoId,
            "Reatribuição do mesmo plano \"" + novoPlano.getNome() + "\" (sem efeito no saldo).");
      } else {
        Plano planoAtual = planoService.requirePlano(assinatura.getPlanoId());
        int diferencial = novoPlano.getCotaMensalCreditos() - planoAtual.getCotaMensalCreditos();
        assinatura.trocarPlano(novoPlanoId);
        assinaturaRepository.save(assinatura);
        creditWalletService.creditarCota(estabelecimentoId, diferencial, assinatura.getId());
        auditService.record(
            adminId,
            "trocar_plano",
            "estabelecimento",
            estabelecimentoId,
            "Troca de \"" + planoAtual.getNome() + "\" para \"" + novoPlano.getNome() + "\".");
      }
    }

    return creditReportingService.buildSummary(estabelecimentoId, assinatura, novoPlano);
  }
}
