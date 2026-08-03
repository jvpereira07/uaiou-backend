package com.uaiou.credits.service;

import com.uaiou.credits.CreditTransactionType;
import com.uaiou.credits.entity.CarteiraCreditos;
import com.uaiou.credits.entity.TransacaoCredito;
import com.uaiou.credits.repository.CarteiraCreditosRepository;
import com.uaiou.credits.repository.TransacaoCreditoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.id.UuidV7;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.6/RF-09.7/RF-09.11 — mecânica pura da carteira de créditos, sem saber nada de plano,
 * assinatura ou admin: qualquer módulo que precise debitar/creditar créditos (T-11 publicando
 * pedido, o módulo {@code admin} atribuindo plano ou fazendo ajuste financeiro) chama isto, nunca
 * escreve em {@code carteira_creditos}/{@code transacao_credito} diretamente.
 */
@Service
public class CreditWalletService {

  private final CarteiraCreditosRepository carteiraCreditosRepository;
  private final TransacaoCreditoRepository transacaoCreditoRepository;

  public CreditWalletService(
      CarteiraCreditosRepository carteiraCreditosRepository,
      TransacaoCreditoRepository transacaoCreditoRepository) {
    this.carteiraCreditosRepository = carteiraCreditosRepository;
    this.transacaoCreditoRepository = transacaoCreditoRepository;
  }

  /**
   * RF-09.6 — consumo ao publicar pedido (T-11 chama isto dentro da própria transação de criação do
   * pedido). RF-09.11: {@code findByIdForUpdate} bloqueia a linha antes de checar o saldo, então
   * duas chamadas concorrentes nunca leem o mesmo saldo "antes" do débito uma da outra — a segunda
   * espera o lock da primeira liberar e já vê o saldo atualizado.
   */
  @Transactional
  public void consumirParaPedido(UUID estabelecimentoId, int quantidade, UUID pedidoId) {
    CarteiraCreditos carteira = lockOuCriar(estabelecimentoId);
    if (carteira.getSaldoCreditos() < quantidade) {
      throw new BusinessRuleException(
          "INSUFFICIENT_CREDITS", "Créditos insuficientes para publicar o pedido.", "RN-05.1");
    }
    carteira.debitar(quantidade);
    carteiraCreditosRepository.save(carteira);
    transacaoCreditoRepository.save(
        new TransacaoCredito(
            UuidV7.next(),
            estabelecimentoId,
            CreditTransactionType.POSTING_CONSUMPTION,
            -quantidade,
            pedidoId,
            null));
  }

  /**
   * RF-09.2/RF-09.3/RF-09.4 — crédito de cota (atribuição inicial, diferencial de troca de plano ou
   * renovação de ciclo). Quantidade {@code <= 0} é um no-op silencioso de propósito: é exatamente o
   * que RF-09.3 pede para troca de plano menor ou reatribuição do mesmo plano (critério de aceite 1
   * de T-09 — repetir a mesma atribuição não credita de novo) — o chamador não precisa checar
   * antes.
   */
  @Transactional
  public void creditarCota(UUID estabelecimentoId, int quantidade, UUID assinaturaId) {
    if (quantidade <= 0) {
      return;
    }
    CarteiraCreditos carteira = lockOuCriar(estabelecimentoId);
    carteira.creditar(quantidade);
    carteiraCreditosRepository.save(carteira);
    transacaoCreditoRepository.save(
        new TransacaoCredito(
            UuidV7.next(),
            estabelecimentoId,
            CreditTransactionType.MONTHLY_QUOTA,
            quantidade,
            null,
            assinaturaId));
  }

  /** RF-09.10 — ajuste administrativo: positivo credita, negativo debita, nunca zero. */
  @Transactional
  public void ajustar(UUID estabelecimentoId, int delta) {
    if (delta == 0) {
      throw new BadRequestException("INVALID_FIELD", "\"amount\" não pode ser zero.");
    }
    CarteiraCreditos carteira = lockOuCriar(estabelecimentoId);
    if (delta < 0 && carteira.getSaldoCreditos() + delta < 0) {
      throw new BusinessRuleException(
          "INSUFFICIENT_CREDITS", "Ajuste deixaria o saldo negativo.", "RN-05.1");
    }
    carteira.ajustar(delta);
    carteiraCreditosRepository.save(carteira);
    transacaoCreditoRepository.save(
        new TransacaoCredito(
            UuidV7.next(), estabelecimentoId, CreditTransactionType.ADJUSTMENT, delta, null, null));
  }

  private CarteiraCreditos lockOuCriar(UUID estabelecimentoId) {
    return carteiraCreditosRepository
        .findByIdForUpdate(estabelecimentoId)
        .orElseGet(() -> new CarteiraCreditos(estabelecimentoId));
  }
}
