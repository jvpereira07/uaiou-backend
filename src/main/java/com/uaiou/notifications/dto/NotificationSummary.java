package com.uaiou.notifications.dto;

import com.uaiou.notifications.NotificationPriority;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Uma linha de {@code GET /me/notifications} (api/notificacoes.md). */
public record NotificationSummary(
    UUID id,
    String type,
    NotificationPriority priority,
    String title,
    String body,
    Map<String, Object> payload,
    Instant readAt,
    Instant createdAt) {}
