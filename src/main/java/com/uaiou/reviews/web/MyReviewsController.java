package com.uaiou.reviews.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.reviews.dto.PendingReviewEntry;
import com.uaiou.reviews.service.AvaliacaoService;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me/reviews} (RF-19.8) — {@code ?direction=pending|received}. */
@RestController
@RequestMapping("/me/reviews")
public class MyReviewsController {

  private final AvaliacaoService avaliacaoService;
  private final CurrentUserHolder currentUserHolder;

  public MyReviewsController(
      AvaliacaoService avaliacaoService, CurrentUserHolder currentUserHolder) {
    this.avaliacaoService = avaliacaoService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public Object list(@RequestParam(defaultValue = "pending") String direction) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER && user.role() != Role.MERCHANT) {
      throw new ForbiddenException(
          "ROLE_NOT_SUPPORTED", "Esta rota é de entregador ou estabelecimento.");
    }
    return switch (direction) {
      case "pending" -> pending(user);
      case "received" -> avaliacaoService.received(user.userId());
      default ->
          throw new BadRequestException(
              "UNSUPPORTED_DIRECTION", "\"direction\" deve ser \"pending\" ou \"received\".");
    };
  }

  private List<PendingReviewEntry> pending(AuthenticatedUser user) {
    return avaliacaoService.pending(user.userId(), user.role() == Role.COURIER);
  }
}
