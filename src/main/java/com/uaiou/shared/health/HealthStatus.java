package com.uaiou.shared.health;

/** {@code status} e {@code database} valem "UP" ou "DOWN". */
public record HealthStatus(String status, String version, String database) {}
