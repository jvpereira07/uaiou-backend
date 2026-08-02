package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AuditLogEntry;
import com.uaiou.admin.dto.CreateSanctionRequest;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.SanctionType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * RF-07.9 e critério de aceite 8 de T-07: {@code GET /admin/audit-logs} filtra por admin/ação/
 * referência, e nenhum verbo de escrita existe nessa rota.
 */
class AuditLogsIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void filtersByAdminAndAction() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    restTemplate.exchange(
        baseUrl("/admin/users/" + courier.id() + "/sanctions"),
        HttpMethod.POST,
        authed(
            admin.accessToken(),
            new CreateSanctionRequest(
                SanctionType.SUSPENSION,
                "Filtro de teste.",
                Instant.now().plus(1, ChronoUnit.DAYS))),
        SanctionSummary.class);

    String url =
        UriComponentsBuilder.fromUriString(baseUrl("/admin/audit-logs"))
            .queryParam("adminId", admin.adminId())
            .queryParam("action", "suspender_usuario")
            .toUriString();
    PageResponse<AuditLogEntry> page =
        restTemplate
            .exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin.accessToken())),
                new ParameterizedTypeReference<PageResponse<AuditLogEntry>>() {})
            .getBody();

    assertThat(page.data()).isNotEmpty();
    assertThat(page.data())
        .allSatisfy(
            entry -> {
              assertThat(entry.action()).isEqualTo("suspender_usuario");
              assertThat(entry.admin().id()).isEqualTo(admin.adminId());
            });
  }

  /**
   * Nenhum verbo de escrita é aceito nesta rota — {@code HttpRequestMethodNotSupportedException} já
   * é convertida em 400 pelo {@code GlobalExceptionHandler} (convenção de todo o projeto, não
   * específica desta rota), então o que importa aqui é "nunca 2xx", não um código HTTP específico.
   */
  @Test
  void noWriteVerbExistsOnTheAuditLogRoute() {
    var deleteResponse =
        restTemplate.exchange(
            baseUrl("/admin/audit-logs"),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(loginAdmin().accessToken())),
            Void.class);
    assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isFalse();

    var putResponse =
        restTemplate.exchange(
            baseUrl("/admin/audit-logs"),
            HttpMethod.PUT,
            new HttpEntity<>(authHeaders(loginAdmin().accessToken())),
            Void.class);
    assertThat(putResponse.getStatusCode().is2xxSuccessful()).isFalse();
  }
}
