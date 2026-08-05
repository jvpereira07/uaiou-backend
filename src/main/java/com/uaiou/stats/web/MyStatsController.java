package com.uaiou.stats.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.stats.dto.SeriesResponse;
import com.uaiou.stats.service.StatsSeriesService;
import com.uaiou.stats.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /me/stats} / {@code GET /me/stats/series} (RF-22.1) — escopo travado no token: não
 * existe parâmetro de usuário em rota nenhuma deste controller.
 */
@RestController
@RequestMapping("/me/stats")
public class MyStatsController {

  private final StatsService statsService;
  private final StatsSeriesService statsSeriesService;
  private final CurrentUserHolder currentUserHolder;

  public MyStatsController(
      StatsService statsService,
      StatsSeriesService statsSeriesService,
      CurrentUserHolder currentUserHolder) {
    this.statsService = statsService;
    this.statsSeriesService = statsSeriesService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public Object get(@RequestParam(required = false) String period) {
    AuthenticatedUser user = currentUserHolder.require();
    return switch (user.role()) {
      case COURIER -> statsService.courierStats(user.userId(), period);
      case MERCHANT -> statsService.merchantStats(user.userId(), period);
      default ->
          throw new ForbiddenException(
              "ROLE_NOT_SUPPORTED", "Esta rota é de entregador ou estabelecimento.");
    };
  }

  @GetMapping("/series")
  public SeriesResponse series(
      @RequestParam String metric, @RequestParam(required = false) String granularity) {
    AuthenticatedUser user = currentUserHolder.require();
    return switch (user.role()) {
      case COURIER -> statsSeriesService.courierSeries(user.userId(), metric, granularity);
      case MERCHANT -> statsSeriesService.merchantSeries(user.userId(), metric, granularity);
      default ->
          throw new ForbiddenException(
              "ROLE_NOT_SUPPORTED", "Esta rota é de entregador ou estabelecimento.");
    };
  }
}
