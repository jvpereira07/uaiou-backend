package com.uaiou.score.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.score.dto.ScoreResponse;
import com.uaiou.score.service.MyScoreService;
import com.uaiou.shared.error.ForbiddenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /me/score} — RF-20.5. Somente leitura (RF-20.7): não existe rota de escrita nem para
 * admin, correção se faz corrigindo o insumo.
 */
@RestController
@RequestMapping("/me/score")
public class MyScoreController {

  private final MyScoreService myScoreService;
  private final CurrentUserHolder currentUserHolder;

  public MyScoreController(MyScoreService myScoreService, CurrentUserHolder currentUserHolder) {
    this.myScoreService = myScoreService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public ScoreResponse get() {
    AuthenticatedUser user = currentUserHolder.require();
    return switch (user.role()) {
      case COURIER -> myScoreService.getEntregador(user.userId());
      case MERCHANT -> myScoreService.getEstabelecimento(user.userId());
      default ->
          throw new ForbiddenException(
              "ROLE_NOT_SUPPORTED", "Esta rota é de entregador ou estabelecimento.");
    };
  }
}
