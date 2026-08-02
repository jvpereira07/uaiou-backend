package com.uaiou.users.service;

import com.uaiou.auth.repository.RefreshTokenRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.UnauthorizedException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import com.uaiou.users.dto.Address;
import com.uaiou.users.dto.MeProfile;
import com.uaiou.users.dto.MeResponse;
import com.uaiou.users.dto.PatchMeProfile;
import com.uaiou.users.dto.PatchMeRequest;
import com.uaiou.users.entity.DocumentoCadastro;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.DocumentoCadastroRepository;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-04.1 a RF-04.7 — `/me` como rota de bootstrap e edição de perfil particionada por
 * sensibilidade.
 */
@Service
public class ProfileService {

  private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

  private static final String TIPO_DOCUMENTO_IDENTIDADE = "documento_identidade";
  private static final String TIPO_DOCUMENTO_VEICULO = "documento_veiculo";
  private static final String TIPO_DOCUMENTO_CNPJ = "documento_cnpj";

  private final UsuarioRepository usuarioRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final EntregadorRepository entregadorRepository;
  private final DocumentoCadastroRepository documentoCadastroRepository;
  private final UploadService uploadService;
  private final RefreshTokenRepository refreshTokenRepository;

  public ProfileService(
      UsuarioRepository usuarioRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      EntregadorRepository entregadorRepository,
      DocumentoCadastroRepository documentoCadastroRepository,
      UploadService uploadService,
      RefreshTokenRepository refreshTokenRepository) {
    this.usuarioRepository = usuarioRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.entregadorRepository = entregadorRepository;
    this.documentoCadastroRepository = documentoCadastroRepository;
    this.uploadService = uploadService;
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional(readOnly = true)
  public MeResponse getMe(UUID usuarioId) {
    Usuario usuario = requireUsuario(usuarioId);
    return buildResponse(usuario, null);
  }

  @Transactional
  public MeResponse patchMe(UUID usuarioId, PatchMeRequest request) {
    Usuario usuario = requireUsuario(usuarioId);

    if (request.displayName() != null) {
      usuario.atualizarNomeExibicao(request.displayName());
    }
    if (request.telefone() != null) {
      usuario.atualizarTelefone(request.telefone());
    }

    List<String> pendingFields = new ArrayList<>();
    if (request.profile() != null) {
      switch (usuario.getTipo()) {
        case COURIER -> pendingFields.addAll(applyCourierProfile(usuario, request.profile()));
        case MERCHANT -> pendingFields.addAll(applyMerchantProfile(usuario, request.profile()));
        case ADMIN ->
            throw new BadRequestException(
                "PROFILE_NOT_EDITABLE",
                "Conta de administrador não tem perfil editável por esta rota.");
      }
    }

    return buildResponse(usuario, pendingFields);
  }

  @Transactional
  public void revokeAllSessions(UUID usuarioId) {
    refreshTokenRepository.revogarTodosDoUsuario(usuarioId, Instant.now());
  }

  private List<String> applyCourierProfile(Usuario usuario, PatchMeProfile profile) {
    rejectFieldsNotApplicable(
        Role.COURIER,
        profile.cnpj() != null
            || profile.cnpjUploadId() != null
            || profile.logoObjectKey() != null
            || profile.bairro() != null
            || profile.rua() != null
            || profile.numero() != null
            || profile.cidade() != null
            || profile.cep() != null);

    rejectOrphanUploadId(
        profile.identityUploadId(), profile.cpf() != null, "identityUploadId", "cpf");

    List<String> pending = new ArrayList<>();

    if (profile.cpf() != null) {
      validateVerificationUpload(
          profile.identityUploadId(),
          usuario.getId(),
          Purpose.IDENTITY_DOCUMENT,
          "identityUploadId");
      createPendingDocument(usuario.getId(), TIPO_DOCUMENTO_IDENTIDADE, profile.identityUploadId());
      pending.add("cpf");
    }

    boolean vehicleFieldsChanged = profile.vehicleType() != null || profile.vehiclePlate() != null;
    rejectOrphanUploadId(
        profile.vehicleUploadId(),
        vehicleFieldsChanged,
        "vehicleUploadId",
        "vehicleType\"/\"vehiclePlate");
    if (vehicleFieldsChanged) {
      validateVerificationUpload(
          profile.vehicleUploadId(), usuario.getId(), Purpose.VEHICLE_DOCUMENT, "vehicleUploadId");
      createPendingDocument(usuario.getId(), TIPO_DOCUMENTO_VEICULO, profile.vehicleUploadId());
      if (profile.vehicleType() != null) {
        pending.add("vehicleType");
      }
      if (profile.vehiclePlate() != null) {
        pending.add("vehiclePlate");
      }
    }

    return pending;
  }

  private List<String> applyMerchantProfile(Usuario usuario, PatchMeProfile profile) {
    rejectFieldsNotApplicable(
        Role.MERCHANT,
        profile.cpf() != null
            || profile.identityUploadId() != null
            || profile.vehicleType() != null
            || profile.vehiclePlate() != null
            || profile.vehicleUploadId() != null);

    rejectOrphanUploadId(profile.cnpjUploadId(), profile.cnpj() != null, "cnpjUploadId", "cnpj");

    Estabelecimento estabelecimento = requireEstabelecimento(usuario);
    List<String> pending = new ArrayList<>();

    if (profile.cnpj() != null) {
      validateVerificationUpload(
          profile.cnpjUploadId(), usuario.getId(), Purpose.CNPJ_DOCUMENT, "cnpjUploadId");
      createPendingDocument(usuario.getId(), TIPO_DOCUMENTO_CNPJ, profile.cnpjUploadId());
      pending.add("cnpj");
    }

    if (profile.logoObjectKey() != null) {
      estabelecimento.atualizarLogo(profile.logoObjectKey());
    }

    boolean addressProvided =
        profile.bairro() != null
            || profile.rua() != null
            || profile.numero() != null
            || profile.cidade() != null
            || profile.cep() != null;
    if (addressProvided) {
      estabelecimento.atualizarEndereco(
          profile.bairro(), profile.rua(), profile.numero(), profile.cidade(), profile.cep());
    }

    return pending;
  }

  private void rejectFieldsNotApplicable(Role role, boolean hasForeignFields) {
    if (hasForeignFields) {
      throw new BadRequestException(
          "PROFILE_FIELD_NOT_APPLICABLE",
          "Um ou mais campos enviados não existem para uma conta " + role + ".");
    }
  }

  /**
   * Um {@code uploadId} só faz sentido junto do valor que ele comprova — sem isso, ficaria um
   * upload "gasto" silenciosamente sem nenhum campo realmente entrando em análise, e o cliente não
   * teria como saber que nada aconteceu.
   */
  private void rejectOrphanUploadId(
      UUID uploadId, boolean pairedValuePresent, String uploadFieldName, String valueFieldName) {
    if (uploadId != null && !pairedValuePresent) {
      throw new BadRequestException(
          "UNEXPECTED_FIELD",
          "\""
              + uploadFieldName
              + "\" foi enviado sem \""
              + valueFieldName
              + "\", que é o campo que ele deveria comprovar.");
    }
  }

  /**
   * Campo verificado só aplica com uma prova pronta e do próprio usuário (RF-04.3) — a checagem em
   * si (existe, é do usuário, está {@code ready}, é do propósito certo) é a "operação de vínculo"
   * compartilhada de RF-05.4 ({@link UploadService#validateForConsumption}); só quem sabe que este
   * upload seria reusado em outro {@code documento_cadastro} é este módulo, então essa parte fica
   * aqui.
   */
  private void validateVerificationUpload(
      UUID uploadId, UUID usuarioId, Purpose tipoEsperado, String fieldName) {
    if (uploadId == null) {
      throw new BadRequestException(
          "MISSING_FIELD", "\"" + fieldName + "\" é obrigatório para confirmar esse campo.");
    }
    uploadService.validateForConsumption(uploadId, usuarioId, tipoEsperado);
    if (documentoCadastroRepository.existsByUploadId(uploadId)) {
      throw new ConflictException(
          "UPLOAD_ALREADY_USED", "Este upload já foi usado em outro documento de cadastro.");
    }
  }

  /**
   * {@code saveAndFlush} + captura de {@link DataIntegrityViolationException} de propósito: a
   * checagem de {@code existsByUploadId} em {@link #validateVerificationUpload} previne o caso
   * comum, mas duas requisições concorrentes reusando o mesmo upload correm até aqui — é a UNIQUE
   * do banco (V13) quem decide de verdade, e sem isso a violação vazaria como 500 em vez do 409 que
   * a checagem prévia já dá.
   */
  private void createPendingDocument(UUID usuarioId, String tipo, UUID uploadId) {
    try {
      documentoCadastroRepository.saveAndFlush(
          new DocumentoCadastro(UuidV7.next(), usuarioId, tipo, uploadId));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "UPLOAD_ALREADY_USED", "Este upload já foi usado em outro documento de cadastro.");
    }
  }

  private MeResponse buildResponse(Usuario usuario, List<String> pendingFields) {
    MeProfile profile =
        switch (usuario.getTipo()) {
          case COURIER -> buildCourierProfile(requireEntregador(usuario));
          case MERCHANT -> buildMerchantProfile(requireEstabelecimento(usuario));
          case ADMIN -> null;
        };

    return new MeResponse(
        usuario.getId(),
        usuario.getTipo(),
        usuario.getStatus(),
        usuario.getNomeExibicao(),
        usuario.getEmail(),
        usuario.getTelefone(),
        profile,
        pendingFields,
        buildLinks(usuario));
  }

  private MeProfile buildCourierProfile(Entregador entregador) {
    return new MeProfile(
        entregador.getCpf(),
        entregador.getVeiculoTipo(),
        entregador.getVeiculoPlaca(),
        entregador.getEntregasRealizadas(),
        null,
        null,
        null,
        null,
        entregador.getScore());
  }

  private MeProfile buildMerchantProfile(Estabelecimento estabelecimento) {
    return new MeProfile(
        null,
        null,
        null,
        null,
        estabelecimento.getCnpj(),
        estabelecimento.getNomeFantasia(),
        estabelecimento.getLogoObjectKey(),
        buildAddress(estabelecimento),
        estabelecimento.getScore());
  }

  private Address buildAddress(Estabelecimento estabelecimento) {
    if (estabelecimento.getBairro() == null
        && estabelecimento.getRua() == null
        && estabelecimento.getNumero() == null
        && estabelecimento.getCidade() == null
        && estabelecimento.getCep() == null) {
      return null;
    }
    return new Address(
        estabelecimento.getBairro(),
        estabelecimento.getRua(),
        estabelecimento.getNumero(),
        estabelecimento.getCidade(),
        estabelecimento.getCep());
  }

  /** RF-04.2 — a tela inicial do cliente nasce destes links, não de lógica espalhada no app. */
  private Map<String, LinkRef> buildLinks(Usuario usuario) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/me"));

    if (usuario.getStatus() == UserStatus.PENDING) {
      links.put("documents", new LinkRef("/api/v1/me/documents", "POST"));
      return links;
    }
    if (usuario.getStatus() == UserStatus.SUSPENDED) {
      links.put("support", new LinkRef("/api/v1/support-tickets", "POST"));
      return links;
    }
    if (usuario.getStatus() == UserStatus.ACTIVE) {
      if (usuario.getTipo() == Role.COURIER) {
        links.put("availability", new LinkRef("/api/v1/me/availability", "PUT"));
        links.put("location", new LinkRef("/api/v1/me/location", "PUT"));
        links.put("openOrders", LinkRef.get("/api/v1/orders?status=published"));
        links.put("wallet", LinkRef.get("/api/v1/me/wallet"));
        links.put("score", LinkRef.get("/api/v1/me/score"));
      } else if (usuario.getTipo() == Role.MERCHANT) {
        links.put("orders", LinkRef.get("/api/v1/orders"));
        links.put("credits", LinkRef.get("/api/v1/me/credits"));
        links.put("blockedCouriers", LinkRef.get("/api/v1/me/blocked-couriers"));
      }
    }
    return links;
  }

  private Usuario requireUsuario(UUID usuarioId) {
    return usuarioRepository
        .findById(usuarioId)
        .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Usuário não encontrado."));
  }

  private Estabelecimento requireEstabelecimento(Usuario usuario) {
    return estabelecimentoRepository
        .findById(usuario.getId())
        .orElseThrow(() -> ctiCorruption(usuario));
  }

  private Entregador requireEntregador(Usuario usuario) {
    return entregadorRepository.findById(usuario.getId()).orElseThrow(() -> ctiCorruption(usuario));
  }

  /**
   * RF-04.5, critério de aceite 7: o banco não expressa que {@code usuario.tipo} deve ter uma
   * linha-filha correspondente (T-02) — se ela faltar, é corrupção, não um caso de uso a tratar
   * graciosamente. Loga ERROR com o suficiente para investigar e estoura para o handler genérico
   * (500), nunca um perfil parcial.
   */
  private RuntimeException ctiCorruption(Usuario usuario) {
    log.error(
        "Invariante do CTI violada: usuario {} tem tipo={} sem linha-filha correspondente.",
        usuario.getId(),
        usuario.getTipo());
    return new IllegalStateException("Perfil corrompido para o usuário " + usuario.getId());
  }
}
