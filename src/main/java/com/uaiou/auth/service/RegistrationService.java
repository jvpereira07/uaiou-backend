package com.uaiou.auth.service;

import com.uaiou.auth.dto.RegisterProfile;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.Role;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-03.1. */
@Service
public class RegistrationService {

  private static final List<String> COURIER_REQUIRED_DOCUMENTS =
      List.of("IDENTITY_DOCUMENT", "DRIVER_LICENSE", "VEHICLE_DOCUMENT");
  private static final List<String> MERCHANT_REQUIRED_DOCUMENTS = List.of("CNPJ_DOCUMENT");

  private final UsuarioRepository usuarioRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final EntregadorRepository entregadorRepository;
  private final PasswordEncoder passwordEncoder;

  public RegistrationService(
      UsuarioRepository usuarioRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      EntregadorRepository entregadorRepository,
      PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.entregadorRepository = entregadorRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    if (request.role() == Role.ADMIN) {
      // Admin não passa por autorregistro (RF-03.1, ponto 5) — criado por processo interno (T-07).
      throw new BadRequestException(
          "ADMIN_SELF_REGISTRATION_NOT_ALLOWED",
          "Administradores não são criados por autorregistro.");
    }
    requireProfileFieldsForRole(request.role(), request.profile());

    Usuario usuario =
        new Usuario(
            UuidV7.next(),
            request.login(),
            request.email(),
            passwordEncoder.encode(request.password()),
            null,
            request.role(),
            request.displayName());

    try {
      // Duas escritas, uma transação (RF-03.1): usuário sem perfil correspondente é estado
      // inválido. Cada
      // saveAndFlush força o INSERT a acontecer agora, para uma violação de UNIQUE aparecer aqui —
      // não em
      // outro lugar da transação — e ser traduzida com precisão sobre qual dos dois inserts falhou.
      usuarioRepository.saveAndFlush(usuario);
      switch (request.role()) {
        case COURIER ->
            entregadorRepository.saveAndFlush(
                new Entregador(
                    usuario,
                    request.profile().cpf(),
                    request.profile().vehicleType(),
                    request.profile().vehiclePlate()));
        case MERCHANT -> {
          String businessName =
              request.profile().businessName() != null
                  ? request.profile().businessName()
                  : request.displayName();
          estabelecimentoRepository.saveAndFlush(
              new Estabelecimento(usuario, request.profile().cnpj(), businessName));
        }
        case ADMIN -> throw new IllegalStateException("inatingível — verificado acima");
      }
    } catch (DataIntegrityViolationException e) {
      throw UniqueConstraintTranslator.translate(e);
    }

    return buildResponse(usuario);
  }

  private void requireProfileFieldsForRole(Role role, RegisterProfile profile) {
    if (role == Role.COURIER && isBlank(profile.cpf())) {
      throw new BadRequestException(
          "MISSING_PROFILE_FIELD", "\"profile.cpf\" é obrigatório para entregador.");
    }
    if (role == Role.MERCHANT && isBlank(profile.cnpj())) {
      throw new BadRequestException(
          "MISSING_PROFILE_FIELD", "\"profile.cnpj\" é obrigatório para estabelecimento.");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private RegisterResponse buildResponse(Usuario usuario) {
    List<String> requiredDocuments =
        usuario.getTipo() == Role.COURIER
            ? COURIER_REQUIRED_DOCUMENTS
            : MERCHANT_REQUIRED_DOCUMENTS;

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/me"));
    links.put("documents", new LinkRef("/api/v1/me/documents", "POST"));

    return new RegisterResponse(
        usuario.getId(), usuario.getTipo(), usuario.getStatus(), requiredDocuments, links);
  }
}
