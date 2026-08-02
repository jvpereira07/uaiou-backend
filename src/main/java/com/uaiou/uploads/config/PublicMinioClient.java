package com.uaiou.uploads.config;

import io.minio.MinioClient;

/**
 * Cliente usado só para gerar URL pré-assinada (PUT de escrita, GET de leitura) — nunca para I/O de
 * objeto.
 */
public record PublicMinioClient(MinioClient client) {}
