package com.uaiou.uploads.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code internalEndpoint} é o host que o BACKEND usa para falar com o MinIO; {@code
 * publicEndpoint} é o host que o CLIENTE usa para seguir a URL pré-assinada — dentro do
 * docker-compose são hosts diferentes (nome do serviço vs. porta publicada no host), e confundir os
 * dois produz uma URL assinada que o cliente externo nunca consegue alcançar.
 */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
    String internalEndpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket) {}
