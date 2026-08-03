package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.PageMeta;
import com.uaiou.shared.pagination.PageResponse;
import java.util.List;
import java.util.Map;

/**
 * Envelope paginado padrão + {@code warning} (RF-11.8): entregador com posição além do frescor
 * recebe lista vazia com {@code LOCATION_STALE}, em vez de um resultado calculado sobre posição
 * obsoleta — que seria pior que lista vazia, porque pareceria correto.
 *
 * <p>Record próprio em vez de {@link PageResponse}: o campo extra é específico desta rota, e mexer
 * no envelope compartilhado por causa de um caso só contaminaria toda listagem da API.
 */
public record OrderListResponse(
    List<OrderSummary> data,
    PageMeta meta,
    @JsonProperty("_links") Map<String, LinkRef> links,
    String warning) {

  public static OrderListResponse de(PageResponse<OrderSummary> page, String warning) {
    return new OrderListResponse(page.data(), page.meta(), page.links(), warning);
  }
}
