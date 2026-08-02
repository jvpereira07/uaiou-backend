package com.uaiou.shared.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaginatorTest {

  @Test
  void defaultsPageToOneAndPerPageToTwenty() {
    PagingRequest request = PagingRequest.of(null, null);

    assertThat(request.page()).isEqualTo(1);
    assertThat(request.perPage()).isEqualTo(20);
  }

  @Test
  void clampsPerPageAboveTheCeilingInsteadOfRejecting() {
    PagingRequest request = PagingRequest.of(1, 500);

    assertThat(request.perPage()).isEqualTo(100);
  }

  @Test
  void clampsPerPageBelowOneToOne() {
    PagingRequest request = PagingRequest.of(1, 0);

    assertThat(request.perPage()).isEqualTo(1);
  }

  @Test
  void clampsPageBelowOneToOne() {
    PagingRequest request = PagingRequest.of(-5, 20);

    assertThat(request.page()).isEqualTo(1);
  }

  @Test
  void selfLinkAlwaysPresentNextAndPrevOnlyWhenApplicable() {
    PagingRequest middlePage = PagingRequest.of(2, 10);
    PageResponse<String> response =
        Paginator.paginate(List.of("a", "b"), 25, middlePage, "/api/v1/things");

    assertThat(response.links()).containsKey("self");
    assertThat(response.links()).containsKey("next");
    assertThat(response.links()).containsKey("prev");
  }

  @Test
  void firstPageHasNoPrevLink() {
    PageResponse<String> response =
        Paginator.paginate(List.of("a"), 25, PagingRequest.of(1, 10), "/api/v1/things");

    assertThat(response.links()).doesNotContainKey("prev");
    assertThat(response.links()).containsKey("next");
  }

  @Test
  void lastPageHasNoNextLink() {
    PageResponse<String> response =
        Paginator.paginate(List.of("a"), 25, PagingRequest.of(3, 10), "/api/v1/things");

    assertThat(response.links()).containsKey("prev");
    assertThat(response.links()).doesNotContainKey("next");
  }

  @Test
  void metaReflectsTheRequestedPageAndTheRealTotal() {
    PageResponse<String> response =
        Paginator.paginate(List.of("a", "b", "c"), 137, PagingRequest.of(2, 20), "/api/v1/things");

    assertThat(response.meta()).isEqualTo(new PageMeta(2, 20, 137));
  }
}
