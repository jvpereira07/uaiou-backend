package com.uaiou.feira.service;

import com.uaiou.auth.service.SessionService;
import com.uaiou.feira.dto.FeiraDtos.EntrarRequest;
import com.uaiou.feira.dto.FeiraDtos.EntrarResponse;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.Role;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrada do jogador no modo feira (docs/feira/01-fluxos.md, fluxo 1): um passo, sem senha, sem
 * documento, sem moderação. Quem entra já sai com sessão válida.
 */
@Service
public class FeiraPlayerService {

  /**
   * {@code usuario} exige senha ou google_id (ck_usuario_credencial). O jogador não tem nenhum dos
   * dois, então recebe um hash com formato bcrypt válido que não corresponde a senha alguma: o
   * grant "password" nunca casa com ele, e a constraint continua satisfeita.
   */
  private static final String SENHA_INUTILIZAVEL =
      "$2a$10$feiraPlaceholderHashNotUsableForLogin.0000000000fe";

  private static final String DOMINIO_SINTETICO = "@feira.uaiou.local";

  private final UsuarioRepository usuarioRepository;
  private final EntregadorRepository entregadorRepository;
  private final SessionService sessionService;

  public FeiraPlayerService(
      UsuarioRepository usuarioRepository,
      EntregadorRepository entregadorRepository,
      SessionService sessionService) {
    this.usuarioRepository = usuarioRepository;
    this.entregadorRepository = entregadorRepository;
    this.sessionService = sessionService;
  }

  /**
   * Reentrar com o mesmo nome de usuário devolve a mesma conta, em vez de 409: quem fecha o app no
   * meio da feira precisa voltar para as próprias capturas, e o estande não tem como fazer suporte
   * de conta. O efeito colateral aceito é que qualquer pessoa pode assumir um nome já usado — numa
   * demonstração de estande isso é barato, num produto seria inaceitável.
   */
  @Transactional
  public EntrarResponse entrar(EntrarRequest request) {
    String username = normalizar(request.username());

    Optional<Entregador> existente = entregadorRepository.findByUsername(username);
    if (existente.isPresent()) {
      Usuario usuario = existente.get().getUsuario();
      return new EntrarResponse(sessionService.issueSessionWithoutCredentials(usuario), false);
    }

    Usuario usuario =
        new Usuario(
            UuidV7.next(),
            username,
            username + DOMINIO_SINTETICO,
            SENHA_INUTILIZAVEL,
            null,
            Role.COURIER,
            request.nome().trim());
    // Validação automática (docs/feira/00-conceito.md): nada na feira espera decisão humana.
    usuario.aprovar();

    try {
      usuarioRepository.saveAndFlush(usuario);
      entregadorRepository.saveAndFlush(Entregador.jogadorDeFeira(usuario, username));
    } catch (DataIntegrityViolationException e) {
      // Duas pessoas digitando o mesmo nome ao mesmo tempo: a unique decide, e quem perdeu tenta
      // outro nome. Não vira reentrada silenciosa porque o vencedor pode ser a outra pessoa.
      throw new ConflictException(
          "USERNAME_INDISPONIVEL", "Este nome de usuário acabou de ser usado. Escolha outro.");
    }

    return new EntrarResponse(sessionService.issueSessionWithoutCredentials(usuario), true);
  }

  /**
   * Nome de usuário é identidade visível no painel: normaliza para minúsculas sem acento e só
   * {@code [a-z0-9_]}, para "João", "joao" e "JOAO" não virarem três jogadores diferentes.
   */
  private String normalizar(String bruto) {
    String semAcento =
        Normalizer.normalize(bruto.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    String normalizado = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    if (normalizado.length() < 3 || normalizado.length() > 20) {
      throw new BadRequestException(
          "INVALID_USERNAME", "O nome de usuário precisa ter de 3 a 20 letras, números ou \"_\".");
    }
    return normalizado;
  }
}
