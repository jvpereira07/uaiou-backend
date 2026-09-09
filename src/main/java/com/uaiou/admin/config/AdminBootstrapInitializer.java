package com.uaiou.admin.config;

import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.Role;
import com.uaiou.users.entity.Admin;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.AdminRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o administrador inicial quando ele ainda não existe. Análogo ao {@code
 * MinioBucketInitializer}: um ambiente novo precisa estar utilizável logo após o primeiro boot.
 *
 * <p>A verificação é por e-mail, não por "existe algum admin": promover ou remover admins é
 * operação normal do painel, e reagir à ausência deles recriaria a conta padrão toda vez que o
 * último fosse removido — exatamente a conta de credencial conhecida que se quer eliminar.
 *
 * <p>Nunca sobrescreve o usuário existente. Se o e-mail já está no banco com senha trocada — o
 * caminho esperado após o primeiro acesso —, este runner não faz nada.
 */
@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

  private final UsuarioRepository usuarioRepository;
  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final AdminBootstrapProperties properties;

  public AdminBootstrapInitializer(
      UsuarioRepository usuarioRepository,
      AdminRepository adminRepository,
      PasswordEncoder passwordEncoder,
      AdminBootstrapProperties properties) {
    this.usuarioRepository = usuarioRepository;
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Optional<Usuario> existente = usuarioRepository.findByEmail(properties.email());
    if (existente.isPresent()) {
      garantirPapelAdmin(existente.get());
      return;
    }
    if (usuarioRepository.existsByLogin(properties.login())) {
      // O login é único: outro usuário já o ocupa. Criar aqui violaria a constraint e derrubaria o
      // boot por um detalhe de configuração — recusar com um aviso mantém a aplicação no ar.
      log.warn(
          "Admin inicial não criado: o login \"{}\" já pertence a outro usuário. "
              + "Ajuste ADMIN_DEFAULT_LOGIN ou promova a conta existente pelo painel.",
          properties.login());
      return;
    }

    Usuario usuario =
        new Usuario(
            UuidV7.next(),
            properties.login(),
            properties.email(),
            passwordEncoder.encode(properties.password()),
            null,
            Role.ADMIN,
            "Administrador");
    // Nasce PENDING (moderação de cadastro de entregador/estabelecimento). Um admin que precisa de
    // aprovação não teria quem o aprovasse: este é o único usuário que se ativa sozinho.
    usuario.aprovar();
    usuarioRepository.saveAndFlush(usuario);
    adminRepository.saveAndFlush(new Admin(usuario, properties.nivel()));

    log.warn(
        "Administrador inicial criado com e-mail \"{}\" e a senha padrão de configuração. "
            + "Troque a senha no primeiro acesso.",
        properties.email());
  }

  private void garantirPapelAdmin(Usuario usuario) {
    if (usuario.getTipo() != Role.ADMIN) {
      log.warn(
          "ADMIN_DEFAULT_EMAIL aponta para um usuário do tipo {}. Nenhuma alteração feita — "
              + "mudar o papel de uma conta existente é decisão do painel, não do boot.",
          usuario.getTipo());
      return;
    }
    if (adminRepository.findById(usuario.getId()).isEmpty()) {
      // Usuário marcado como ADMIN mas sem a linha em "admin": estado inconsistente que impediria o
      // acesso ao painel. Completar é seguro; não toca em credencial nenhuma.
      adminRepository.saveAndFlush(new Admin(usuario, properties.nivel()));
      log.info("Vínculo de administrador restaurado para \"{}\".", properties.email());
    }
  }
}
