package com.uaiou.credits.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.credits.dto.CreditTransactionSummary;
import com.uaiou.credits.dto.MyCreditsResponse;
import com.uaiou.credits.service.MyCreditsService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de {@code system-documentation/api/financeiro.md} cobertos por RF-09.8/RF-09.9 (T-09).
 */
@RestController
@RequestMapping("/me/credits")
public class MyCreditsController {

  private final MyCreditsService myCreditsService;
  private final CurrentUserHolder currentUserHolder;

  public MyCreditsController(
      MyCreditsService myCreditsService, CurrentUserHolder currentUserHolder) {
    this.myCreditsService = myCreditsService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public MyCreditsResponse getCredits() {
    AuthenticatedUser user = requireMerchant();
    return myCreditsService.getCredits(user.userId());
  }

  @GetMapping("/transactions")
  public PageResponse<CreditTransactionSummary> transactions(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    AuthenticatedUser user = requireMerchant();
    return myCreditsService.listTransactions(
        user.userId(), PagingRequest.of(page, perPage), "/api/v1/me/credits/transactions");
  }

  private AuthenticatedUser requireMerchant() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Rota restrita a estabelecimentos.");
    }
    return user;
  }
}
