package com.uaiou.blocks.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.blocks.dto.BlockCourierRequest;
import com.uaiou.blocks.dto.BlockedCourierSummary;
import com.uaiou.blocks.service.BloqueioService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/usuarios.md} cobertos por T-12. */
@RestController
@RequestMapping("/me/blocked-couriers")
public class BlockedCouriersController {

  private final BloqueioService bloqueioService;
  private final CurrentUserHolder currentUserHolder;

  public BlockedCouriersController(
      BloqueioService bloqueioService, CurrentUserHolder currentUserHolder) {
    this.bloqueioService = bloqueioService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public List<BlockedCourierSummary> list() {
    return bloqueioService.list(requireMerchant().userId());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BlockedCourierSummary block(@Valid @RequestBody BlockCourierRequest request) {
    return bloqueioService.block(requireMerchant().userId(), request);
  }

  @DeleteMapping("/{courierId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unblock(@PathVariable UUID courierId) {
    bloqueioService.unblock(requireMerchant().userId(), courierId);
  }

  private AuthenticatedUser requireMerchant() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Rota restrita a estabelecimentos.");
    }
    return user;
  }
}
