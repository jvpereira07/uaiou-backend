package com.uaiou.shared.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Referência de navegação exposta em {@code _links}, conforme api/README.md. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkRef(String href, String method) {

  public static LinkRef get(String href) {
    return new LinkRef(href, null);
  }
}
