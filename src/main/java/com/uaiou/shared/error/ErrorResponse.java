package com.uaiou.shared.error;

/** Envelope de erro único de toda a API: {"error": {...}}. */
public record ErrorResponse(ErrorBody error) {}
