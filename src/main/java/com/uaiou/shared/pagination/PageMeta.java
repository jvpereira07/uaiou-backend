package com.uaiou.shared.pagination;

/** Metadados de paginação — mesmo formato em toda listagem da API (api/README.md). */
public record PageMeta(int page, int perPage, long total) {}
