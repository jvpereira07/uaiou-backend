package com.uaiou.uploads.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link MinioProperties} chega via {@code @ConfigurationPropertiesScan} (UaiouApplication), sem
 * precisar de {@code @EnableConfigurationProperties} aqui.
 */
@Configuration
public class MinioClientConfig {

  // MinIO não tem "região" real, mas o SDK só pula a chamada de rede de auto-detecção de região
  // (GetBucketLocation) quando a região vem explícita — sem isto, até GERAR uma URL pré-assinada
  // faz uma
  // chamada de verdade contra o endpoint configurado no cliente. Descoberto rodando contra o
  // docker-compose real: o publicMinioClient aponta para "localhost:9000" (alcançável só de FORA da
  // rede
  // Docker), e essa chamada de auto-detecção — de DENTRO do container do backend — falhava com
  // connection
  // refused.
  private static final String REGION = "us-east-1";

  @Bean
  public InternalMinioClient internalMinioClient(MinioProperties properties) {
    return new InternalMinioClient(
        MinioClient.builder()
            .endpoint(properties.internalEndpoint())
            .region(REGION)
            .credentials(properties.accessKey(), properties.secretKey())
            .build());
  }

  @Bean
  public PublicMinioClient publicMinioClient(MinioProperties properties) {
    return new PublicMinioClient(
        MinioClient.builder()
            .endpoint(properties.publicEndpoint())
            .region(REGION)
            .credentials(properties.accessKey(), properties.secretKey())
            .build());
  }
}
