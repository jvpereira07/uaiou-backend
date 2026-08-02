package com.uaiou.admin.service;

import com.uaiou.users.entity.Sancao;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.SancaoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-07.7: suspensão com prazo vencido não espera o admin — encerra sozinha e devolve a conta a
 * {@code ativo}. Banimento nunca aparece aqui ({@code fim} é sempre nulo, {@link
 * SancaoRepository#findExpired} não o alcança) — só reverte por decisão do admin ({@code DELETE
 * /admin/sanctions/{id}}).
 */
@Component
public class SanctionExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(SanctionExpiryJob.class);

  private final SancaoRepository sancaoRepository;
  private final UsuarioRepository usuarioRepository;

  public SanctionExpiryJob(SancaoRepository sancaoRepository, UsuarioRepository usuarioRepository) {
    this.sancaoRepository = sancaoRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Scheduled(fixedDelayString = "PT10M")
  @Transactional
  public void encerrarSuspensoesVencidas() {
    List<Sancao> expired = sancaoRepository.findExpired(Instant.now());
    for (Sancao sancao : expired) {
      sancao.encerrar();
      usuarioRepository.findById(sancao.getUsuarioAlvoId()).ifPresent(Usuario::reativar);
    }
    if (!expired.isEmpty()) {
      log.info("Expiração de sanções: {} suspensão(ões) vencida(s) encerrada(s).", expired.size());
    }
  }
}
