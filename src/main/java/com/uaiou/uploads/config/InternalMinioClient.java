package com.uaiou.uploads.config;

import io.minio.MinioClient;

/**
 * Cliente usado para I/O de objeto de verdade (statObject/getObject/removeObject/bucket) — nunca
 * para montar URL pré-assinada devolvida ao cliente.
 */
public record InternalMinioClient(MinioClient client) {}
