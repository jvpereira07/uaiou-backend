package com.uaiou.shared.pagination;

/**
 * {@code page}/{@code perPage} já normalizados: página mínima 1, {@code perPage} entre 1 e {@link
 * #MAX_PER_PAGE}. Um valor de {@code perPage} acima do teto é <strong>reduzido</strong>, nunca
 * rejeitado (api/README.md).
 *
 * <p>Nome deliberadamente diferente de {@code org.springframework.data.domain.PageRequest} para não
 * colidir na importação — os dois convivem no mesmo código a partir do momento em que os
 * repositórios usarem Spring Data.
 */
public record PagingRequest(int page, int perPage) {

  public static final int DEFAULT_PER_PAGE = 20;
  public static final int MAX_PER_PAGE = 100;

  public static PagingRequest of(Integer page, Integer perPage) {
    int normalizedPage = (page == null || page < 1) ? 1 : page;
    int normalizedPerPage =
        (perPage == null) ? DEFAULT_PER_PAGE : Math.clamp(perPage, 1, MAX_PER_PAGE);
    return new PagingRequest(normalizedPage, normalizedPerPage);
  }

  /** Offset zero-based para uso em consultas (ex.: {@code Pageable}, LIMIT/OFFSET). */
  public int offset() {
    return (page - 1) * perPage;
  }
}
