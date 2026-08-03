package com.uaiou.credits.service;

import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.dto.UpdatePlanRequest;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.repository.PlanoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.1 — CRUD mínimo do catálogo de planos. Volume esperado (poucas dezenas de planos) não
 * justifica paginação no banco — paginado em memória, mesmo raciocínio da fila de aprovação (T-07).
 */
@Service
public class PlanoService {

  private final PlanoRepository planoRepository;

  public PlanoService(PlanoRepository planoRepository) {
    this.planoRepository = planoRepository;
  }

  @Transactional
  public PlanSummary create(CreatePlanRequest request) {
    if (request.price().isNegative()) {
      throw new BadRequestException("INVALID_FIELD", "\"price\" não pode ser negativo.");
    }
    Plano plano =
        new Plano(UuidV7.next(), request.name(), request.monthlyCredits(), request.price());
    planoRepository.save(plano);
    return toSummary(plano);
  }

  @Transactional
  public PlanSummary update(UUID id, UpdatePlanRequest request) {
    if (request.price() != null && request.price().isNegative()) {
      throw new BadRequestException("INVALID_FIELD", "\"price\" não pode ser negativo.");
    }
    Plano plano = requirePlano(id);
    plano.atualizar(request.name(), request.monthlyCredits(), request.price(), request.active());
    return toSummary(plano);
  }

  @Transactional(readOnly = true)
  public PageResponse<PlanSummary> list(PagingRequest paging, String baseUri) {
    List<PlanSummary> all = planoRepository.findAll().stream().map(this::toSummary).toList();
    int from = Math.min(paging.offset(), all.size());
    int to = Math.min(from + paging.perPage(), all.size());
    return Paginator.paginate(all.subList(from, to), all.size(), paging, baseUri);
  }

  @Transactional(readOnly = true)
  public Plano requirePlano(UUID id) {
    return planoRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plano não encontrado."));
  }

  private PlanSummary toSummary(Plano plano) {
    return new PlanSummary(
        plano.getId(),
        plano.getNome(),
        plano.getCotaMensalCreditos(),
        plano.getPreco(),
        plano.isAtivo(),
        plano.getCriadoEm());
  }
}
