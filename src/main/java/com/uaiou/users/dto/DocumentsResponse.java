package com.uaiou.users.dto;

import com.uaiou.uploads.Purpose;
import java.util.List;

/**
 * RF-06.1: {@code missingTypes} é o que ainda falta para o papel do usuário — a interface sabe o
 * que pedir.
 */
public record DocumentsResponse(List<DocumentSummary> documents, List<Purpose> missingTypes) {}
