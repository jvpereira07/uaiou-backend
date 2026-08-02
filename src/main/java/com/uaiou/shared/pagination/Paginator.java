package com.uaiou.shared.pagination;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Monta {@link PageResponse} a partir de conteúdo já recortado — um único lugar onde {@code
 * meta}/{@code _links} nascem.
 */
public final class Paginator {

  private Paginator() {}

  public static <T> PageResponse<T> paginate(
      List<T> content, long total, PagingRequest request, String baseUri) {
    PageMeta meta = new PageMeta(request.page(), request.perPage(), total);
    Map<String, LinkRef> links = buildLinks(request, total, baseUri);
    return new PageResponse<>(content, meta, links);
  }

  /** Conveniência para quando o conteúdo já vem de um repositório Spring Data. */
  public static <T> PageResponse<T> paginate(Page<T> page, PagingRequest request, String baseUri) {
    return paginate(page.getContent(), page.getTotalElements(), request, baseUri);
  }

  private static Map<String, LinkRef> buildLinks(
      PagingRequest request, long total, String baseUri) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    long totalPages =
        request.perPage() == 0 ? 0 : (total + request.perPage() - 1) / request.perPage();

    links.put("self", LinkRef.get(pageUri(baseUri, request.page(), request.perPage())));
    if (request.page() < totalPages) {
      links.put("next", LinkRef.get(pageUri(baseUri, request.page() + 1, request.perPage())));
    }
    if (request.page() > 1) {
      links.put("prev", LinkRef.get(pageUri(baseUri, request.page() - 1, request.perPage())));
    }
    return links;
  }

  private static String pageUri(String baseUri, int page, int perPage) {
    return UriComponentsBuilder.fromUriString(baseUri)
        .replaceQueryParam("page", page)
        .replaceQueryParam("perPage", perPage)
        .build()
        .toUriString();
  }
}
