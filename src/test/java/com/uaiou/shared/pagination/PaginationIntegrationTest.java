package com.uaiou.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.support.AbstractIntegrationTest;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prova o helper de paginação por HTTP real, contra uma rota que existe só neste teste (critério de
 * aceite 6). Nenhuma listagem de produto foi criada em T-01 — não há regra de negócio aqui, só o
 * contrato genérico.
 */
@Import(PaginationIntegrationTest.TestOnlyController.class)
class PaginationIntegrationTest extends AbstractIntegrationTest {

  @Test
  void defaultsToTwentyPerPage() {
    ResponseEntity<PageResponse<String>> response =
        restTemplate.exchange(
            baseUrl("/_test/paginated"),
            org.springframework.http.HttpMethod.GET,
            null,
            PAGE_RESPONSE_TYPE);

    assertThat(response.getBody().data()).hasSize(20);
    assertThat(response.getBody().meta().perPage()).isEqualTo(20);
    assertThat(response.getBody().meta().total()).isEqualTo(137);
  }

  @Test
  void clampsPerPageToTheCeilingInsteadOfRejecting() {
    ResponseEntity<PageResponse<String>> response =
        restTemplate.exchange(
            baseUrl("/_test/paginated?page=1&perPage=500"),
            org.springframework.http.HttpMethod.GET,
            null,
            PAGE_RESPONSE_TYPE);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody().data()).hasSize(100);
    assertThat(response.getBody().meta().perPage()).isEqualTo(100);
  }

  @Test
  void exposesLinksForNavigation() {
    ResponseEntity<PageResponse<String>> response =
        restTemplate.exchange(
            baseUrl("/_test/paginated?page=2&perPage=50"),
            org.springframework.http.HttpMethod.GET,
            null,
            PAGE_RESPONSE_TYPE);

    assertThat(response.getBody().links()).containsKeys("self", "next", "prev");
  }

  private static final ParameterizedTypeReference<PageResponse<String>> PAGE_RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  @RestController
  public static class TestOnlyController {

    private static final List<String> ALL_ITEMS =
        IntStream.rangeClosed(1, 137).mapToObj(i -> "item-" + i).toList();

    @GetMapping("/_test/paginated")
    public PageResponse<String> list(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer perPage) {
      PagingRequest request = PagingRequest.of(page, perPage);
      List<String> slice =
          ALL_ITEMS.stream().skip(request.offset()).limit(request.perPage()).toList();
      return Paginator.paginate(slice, ALL_ITEMS.size(), request, "/api/v1/_test/paginated");
    }
  }
}
