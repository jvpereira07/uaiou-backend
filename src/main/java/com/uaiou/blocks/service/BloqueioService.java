package com.uaiou.blocks.service;

import com.uaiou.blocks.dto.BlockCourierRequest;
import com.uaiou.blocks.dto.BlockedCourierSummary;
import com.uaiou.blocks.entity.Bloqueio;
import com.uaiou.blocks.repository.BloqueioRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-12.1 a RF-12.8 — o estabelecimento controla quem o atende
 * ([uc-bloqueios](../../docs/casos-de-uso/uc-bloqueios.md)).
 *
 * <p>RF-12.6: <strong>silencioso</strong> — nenhuma notificação ao bloqueado. Avisar transformaria
 * decisão comercial privada em conflito interpessoal, e a RN-07.3 segue em aberto. É por isso que
 * este serviço não fala com o módulo de notificações (T-08), embora ele exista.
 *
 * <p>RF-12.4/RF-12.5: bloquear não toca em pedido nenhum. O efeito é sobre elegibilidade FUTURA —
 * pedido já atribuído segue até a finalização, e a relação do entregador com os demais
 * estabelecimentos não muda.
 */
@Service
public class BloqueioService {

  private final BloqueioRepository bloqueioRepository;
  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;

  public BloqueioService(
      BloqueioRepository bloqueioRepository,
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository) {
    this.bloqueioRepository = bloqueioRepository;
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional(readOnly = true)
  public List<BlockedCourierSummary> list(UUID estabelecimentoId) {
    List<Bloqueio> bloqueios =
        bloqueioRepository.findByEstabelecimentoIdOrderByCriadoEmDesc(estabelecimentoId);
    Map<UUID, String> nomes =
        usuarioRepository
            .findAllById(bloqueios.stream().map(Bloqueio::getEntregadorId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Usuario::getId, Usuario::getNomeExibicao));

    return bloqueios.stream()
        .map(
            b ->
                new BlockedCourierSummary(
                    b.getEntregadorId(),
                    nomes.get(b.getEntregadorId()),
                    b.getMotivo(),
                    b.getCriadoEm()))
        .toList();
  }

  /** RF-12.2 — par repetido é 409 (UK {@code uk_bloqueio_par}, V5). */
  @Transactional
  public BlockedCourierSummary block(UUID estabelecimentoId, BlockCourierRequest request) {
    if (!entregadorRepository.existsById(request.courierId())) {
      throw new NotFoundException("COURIER_NOT_FOUND", "Entregador não encontrado.");
    }
    if (bloqueioRepository.existsByEstabelecimentoIdAndEntregadorId(
        estabelecimentoId, request.courierId())) {
      throw jaBloqueado();
    }

    Bloqueio bloqueio =
        new Bloqueio(UuidV7.next(), estabelecimentoId, request.courierId(), request.reason());
    try {
      bloqueioRepository.saveAndFlush(bloqueio);
    } catch (DataIntegrityViolationException e) {
      // Duas requisições simultâneas passam pelo exists acima; quem decide de verdade é a UK.
      throw jaBloqueado();
    }

    return new BlockedCourierSummary(
        request.courierId(),
        usuarioRepository.findById(request.courierId()).map(Usuario::getNomeExibicao).orElse(null),
        request.reason(),
        bloqueio.getCriadoEm());
  }

  /**
   * RF-12.8 — a política de desbloqueio é {@code TODO(dono)}; enquanto indefinida, a rota permite e
   * o efeito é imediato (o entregador volta à vitrine na listagem seguinte).
   */
  @Transactional
  public void unblock(UUID estabelecimentoId, UUID entregadorId) {
    Bloqueio bloqueio =
        bloqueioRepository
            .findByEstabelecimentoIdAndEntregadorId(estabelecimentoId, entregadorId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "BLOCK_NOT_FOUND", "Este entregador não está bloqueado."));
    bloqueioRepository.delete(bloqueio);
  }

  /**
   * RF-12.3 — guarda que T-13 (aceite) e T-14 (contraoferta) chamam antes de deixar o entregador
   * agir sobre um pedido. Fica aqui, e não repetida em cada rota, para os dois caminhos nunca
   * divergirem sobre o que significa "bloqueado".
   *
   * <p>403 e não 404: o entregador VIU o pedido na vitrine antes do bloqueio entrar em vigor, então
   * esconder a existência agora não protegeria nada — e um erro mudo deixaria o app sem ter o que
   * dizer para o usuário.
   */
  @Transactional(readOnly = true)
  public void requireNotBlocked(UUID estabelecimentoId, UUID entregadorId) {
    if (bloqueioRepository.existsByEstabelecimentoIdAndEntregadorId(
        estabelecimentoId, entregadorId)) {
      throw new ForbiddenException(
          "BLOCKED_BY_MERCHANT", "Este estabelecimento não está aceitando entregas suas.");
    }
  }

  /** RF-12.7 — sinal agregado exposto ao admin (T-07). Na v1 não entra no score. */
  @Transactional(readOnly = true)
  public long distinctMerchantsBlocking(UUID entregadorId) {
    return bloqueioRepository.contarEstabelecimentosDistintosQueBloquearam(entregadorId);
  }

  private ConflictException jaBloqueado() {
    return new ConflictException("ALREADY_BLOCKED", "Este entregador já está bloqueado.");
  }
}
