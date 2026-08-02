package com.uaiou.shared.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Envelope de toda resposta paginada da API: {@code data}, {@code meta} e {@code _links} — nunca
 * uma lista solta no corpo raiz.
 */
public record PageResponse<T>(
    List<T> data, PageMeta meta, @JsonProperty("_links") Map<String, LinkRef> links) {}
