package com.uaiou.shared.health;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Público, sem autenticação (RF-01.12). Usado por CI, orquestrador de containers e, futuramente,
 * pelo painel operacional do admin (T-24).
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private static final int DB_CHECK_TIMEOUT_SECONDS = 2;
  private static final String FALLBACK_VERSION = "dev";

  private final DataSource dataSource;
  private final Optional<BuildProperties> buildProperties;

  public HealthController(DataSource dataSource, Optional<BuildProperties> buildProperties) {
    this.dataSource = dataSource;
    this.buildProperties = buildProperties;
  }

  @GetMapping
  public ResponseEntity<HealthStatus> health() {
    boolean databaseUp = isDatabaseReachable();
    String version = buildProperties.map(BuildProperties::getVersion).orElse(FALLBACK_VERSION);
    HealthStatus body =
        new HealthStatus(databaseUp ? "UP" : "DOWN", version, databaseUp ? "UP" : "DOWN");

    return databaseUp ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
  }

  private boolean isDatabaseReachable() {
    try (Connection connection = dataSource.getConnection()) {
      return connection.isValid(DB_CHECK_TIMEOUT_SECONDS);
    } catch (SQLException e) {
      return false;
    }
  }
}
