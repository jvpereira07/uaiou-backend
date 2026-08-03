package com.uaiou.admin.service;

import com.uaiou.admin.dto.AdminUserDetail;
import com.uaiou.admin.dto.CourierSummary;
import com.uaiou.admin.dto.MerchantSummary;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.blocks.service.BloqueioService;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import com.uaiou.users.dto.Address;
import com.uaiou.users.dto.CourierLocation;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.MeProfile;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.entity.Sancao;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import com.uaiou.users.repository.SancaoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import com.uaiou.users.service.DocumentoCadastroService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-07.8 — listagens de supervisão e o dossiê completo de um usuário. */
@Service
public class SupervisionService {

  private final UsuarioRepository usuarioRepository;
  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final SancaoRepository sancaoRepository;
  private final DocumentoCadastroService documentoCadastroService;
  private final BloqueioService bloqueioService;

  public SupervisionService(
      UsuarioRepository usuarioRepository,
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      SancaoRepository sancaoRepository,
      DocumentoCadastroService documentoCadastroService,
      BloqueioService bloqueioService) {
    this.usuarioRepository = usuarioRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.sancaoRepository = sancaoRepository;
    this.documentoCadastroService = documentoCadastroService;
    this.bloqueioService = bloqueioService;
  }

  @Transactional(readOnly = true)
  public PageResponse<CourierSummary> listCouriers(
      String status, String search, PagingRequest paging, String baseUri) {
    Page<Usuario> page = search(Role.COURIER, status, search, paging);
    Map<UUID, Entregador> entregadores = batchEntregadores(page);
    List<CourierSummary> content =
        page.getContent().stream()
            .map(u -> toCourierSummary(u, entregadores.get(u.getId())))
            .toList();
    return Paginator.paginate(content, page.getTotalElements(), paging, baseUri);
  }

  @Transactional(readOnly = true)
  public PageResponse<MerchantSummary> listMerchants(
      String status, String search, PagingRequest paging, String baseUri) {
    Page<Usuario> page = search(Role.MERCHANT, status, search, paging);
    Map<UUID, Estabelecimento> estabelecimentos = batchEstabelecimentos(page);
    List<MerchantSummary> content =
        page.getContent().stream()
            .map(u -> toMerchantSummary(u, estabelecimentos.get(u.getId())))
            .toList();
    return Paginator.paginate(content, page.getTotalElements(), paging, baseUri);
  }

  @Transactional(readOnly = true)
  public AdminUserDetail getUserDetail(UUID userId) {
    Usuario usuario =
        usuarioRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado."));

    MeProfile profile =
        switch (usuario.getTipo()) {
          case COURIER -> buildCourierProfile(requireEntregador(usuario));
          case MERCHANT -> buildMerchantProfile(requireEstabelecimento(usuario));
          case ADMIN -> null;
        };
    DocumentsResponse documents =
        usuario.getTipo() == Role.ADMIN
            ? new DocumentsResponse(List.of(), List.of())
            : documentoCadastroService.list(userId);
    List<SanctionSummary> sanctions =
        sancaoRepository.findByUsuarioAlvoIdOrderByCriadoEmDesc(userId).stream()
            .map(this::toSanctionSummary)
            .toList();

    return new AdminUserDetail(
        usuario.getId(),
        usuario.getTipo(),
        usuario.getStatus(),
        usuario.getNomeExibicao(),
        usuario.getEmail(),
        usuario.getTelefone(),
        profile,
        documents,
        sanctions,
        // RF-12.7: sinal de moderação só faz sentido para entregador — estabelecimento não é
        // bloqueado por ninguém.
        usuario.getTipo() == Role.COURIER
            ? bloqueioService.distinctMerchantsBlocking(userId)
            : null,
        usuario.getCriadoEm());
  }

  /**
   * Specification, não JPQL fixo com {@code :search is null or ...}: um parâmetro nulo dentro de
   * {@code lower(concat(...))} faz o Postgres falhar ao tentar inferir o tipo do bind (\"function
   * lower(bytea) does not exist\") mesmo quando o {@code or} nunca chega a avaliar aquele lado — o
   * predicado só é adicionado quando o valor existe de verdade, então o Postgres nunca vê o
   * parâmetro ambíguo.
   */
  private Page<Usuario> search(Role role, String status, String search, PagingRequest paging) {
    UserStatus parsedStatus = parseStatus(status);
    String normalizedSearch = (search == null || search.isBlank()) ? null : search;

    Specification<Usuario> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("tipo"), role));
          if (parsedStatus != null) {
            predicates.add(cb.equal(root.get("status"), parsedStatus));
          }
          if (normalizedSearch != null) {
            String pattern = "%" + normalizedSearch.toLowerCase() + "%";
            predicates.add(
                cb.or(
                    cb.like(cb.lower(root.get("nomeExibicao")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.like(cb.lower(root.get("login")), pattern)));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return usuarioRepository.findAll(
        spec,
        PageRequest.of(paging.page() - 1, paging.perPage(), Sort.by("criadoEm").descending()));
  }

  private UserStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return UserStatus.fromJson(status);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("INVALID_STATUS", "\"" + status + "\" não é um status válido.");
    }
  }

  private Map<UUID, Entregador> batchEntregadores(Page<Usuario> page) {
    List<UUID> ids = page.getContent().stream().map(Usuario::getId).toList();
    return entregadorRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Entregador::getUsuarioId, e -> e));
  }

  private Map<UUID, Estabelecimento> batchEstabelecimentos(Page<Usuario> page) {
    List<UUID> ids = page.getContent().stream().map(Usuario::getId).toList();
    return estabelecimentoRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Estabelecimento::getUsuarioId, e -> e));
  }

  private CourierSummary toCourierSummary(Usuario usuario, Entregador entregador) {
    return new CourierSummary(
        usuario.getId(),
        usuario.getNomeExibicao(),
        usuario.getEmail(),
        usuario.getStatus(),
        entregador.getCpf(),
        entregador.getVeiculoTipo(),
        entregador.getVeiculoPlaca(),
        entregador.isDisponivel(),
        entregador.getEntregasRealizadas(),
        entregador.getScore(),
        usuario.getCriadoEm());
  }

  private MerchantSummary toMerchantSummary(Usuario usuario, Estabelecimento estabelecimento) {
    return new MerchantSummary(
        usuario.getId(),
        usuario.getNomeExibicao(),
        usuario.getEmail(),
        usuario.getStatus(),
        estabelecimento.getCnpj(),
        estabelecimento.getNomeFantasia(),
        estabelecimento.getScore(),
        usuario.getCriadoEm());
  }

  private MeProfile buildCourierProfile(Entregador entregador) {
    return new MeProfile(
        entregador.getCpf(),
        entregador.getVeiculoTipo(),
        entregador.getVeiculoPlaca(),
        entregador.isDisponivel(),
        entregador.getLocalizacaoEm() == null
            ? null
            : new CourierLocation(
                entregador.getLat(), entregador.getLongitude(), entregador.getLocalizacaoEm()),
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

  private SanctionSummary toSanctionSummary(Sancao sancao) {
    return new SanctionSummary(
        sancao.getId(),
        sancao.getUsuarioAlvoId(),
        sancao.getTipo(),
        sancao.getMotivo(),
        sancao.getInicio(),
        sancao.getFim(),
        sancao.isAtiva(),
        sancao.getAdminId(),
        sancao.getCriadoEm());
  }

  private Entregador requireEntregador(Usuario usuario) {
    return entregadorRepository.findById(usuario.getId()).orElseThrow(() -> ctiCorruption(usuario));
  }

  private Estabelecimento requireEstabelecimento(Usuario usuario) {
    return estabelecimentoRepository
        .findById(usuario.getId())
        .orElseThrow(() -> ctiCorruption(usuario));
  }

  private RuntimeException ctiCorruption(Usuario usuario) {
    return new IllegalStateException(
        "Perfil corrompido para o usuário " + usuario.getId() + ": linha-filha do CTI ausente.");
  }
}
