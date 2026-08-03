package com.uaiou.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;

/**
 * {@code since} é nulo (e some do JSON) quando {@code available = false} — não existe "desde quando
 * está indisponível". RF-10.10: é este relógio que alimenta horas disponíveis em T-22.
 */
public record AvailabilityResponse(
    boolean available, Instant since, @JsonProperty("_links") Map<String, LinkRef> links) {}
