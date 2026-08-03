package com.uaiou.notifications.dto;

import java.util.List;

/**
 * RF-08.5 — {@code meta.unread} é contagem REAL do usuário, não do recorte da página: é o número
 * que o app põe no badge, e um badge que muda conforme a paginação seria pior que nenhum.
 */
public record NotificationListResponse(List<NotificationSummary> data, Meta meta) {

  public record Meta(int page, int perPage, long total, long unread) {}
}
