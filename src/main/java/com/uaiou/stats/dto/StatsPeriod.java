package com.uaiou.stats.dto;

import java.time.Instant;

/** RF-22.7 — a resposta informa o período fechado que representa. */
public record StatsPeriod(String label, Instant from, Instant to) {}
