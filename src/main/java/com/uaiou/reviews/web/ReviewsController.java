package com.uaiou.reviews.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.reviews.dto.CreateReviewRequest;
import com.uaiou.reviews.dto.ReviewResponse;
import com.uaiou.reviews.service.AvaliacaoService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /orders/{id}/reviews} (RF-19.2) — qualquer parte do pedido, entregador ou
 * estabelecimento.
 */
@RestController
@RequestMapping("/orders/{orderId}/reviews")
public class ReviewsController {

  private final AvaliacaoService avaliacaoService;
  private final CurrentUserHolder currentUserHolder;

  public ReviewsController(AvaliacaoService avaliacaoService, CurrentUserHolder currentUserHolder) {
    this.avaliacaoService = avaliacaoService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReviewResponse create(
      @PathVariable UUID orderId, @Valid @RequestBody CreateReviewRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER && user.role() != Role.MERCHANT) {
      throw new ForbiddenException(
          "ROLE_NOT_SUPPORTED", "Só as partes do pedido avaliam a entrega.");
    }
    return avaliacaoService.create(user.userId(), orderId, request);
  }
}
