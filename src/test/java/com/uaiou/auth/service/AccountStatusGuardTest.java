package com.uaiou.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Sancao;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.SancaoRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountStatusGuardTest {

  private final SancaoRepository sancaoRepository = mock(SancaoRepository.class);
  private final AccountStatusGuard guard = new AccountStatusGuard(sancaoRepository);

  @Test
  void activeUserIsAllowed() {
    assertThatNoException()
        .isThrownBy(() -> guard.checkAllowsSession(usuarioWithStatus(UserStatus.ACTIVE)));
  }

  @Test
  void pendingUserIsAllowed() {
    // pendente recebe token (RF-03.4) — quem barra operação é a rota, não o login.
    assertThatNoException()
        .isThrownBy(() -> guard.checkAllowsSession(usuarioWithStatus(UserStatus.PENDING)));
  }

  @Test
  void bannedUserIsRejectedWithReasonFromSanction() {
    Usuario usuario = usuarioWithStatus(UserStatus.BANNED);
    Sancao sancao = sancaoWith("Fraude confirmada", null);
    when(sancaoRepository.findBlockingSanction(usuario.getId())).thenReturn(Optional.of(sancao));

    assertThatThrownBy(() -> guard.checkAllowsSession(usuario))
        .isInstanceOfSatisfying(
            ForbiddenException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo("ACCOUNT_BANNED");
              assertThat(ex.getMessage()).isEqualTo("Fraude confirmada");
              assertThat(ex.details()).containsEntry("reason", "Fraude confirmada");
              assertThat(ex.details()).doesNotContainKey("until");
            });
  }

  @Test
  void bannedUserWithoutARecordedSanctionStillGetsAGenericReason() {
    Usuario usuario = usuarioWithStatus(UserStatus.BANNED);
    when(sancaoRepository.findBlockingSanction(usuario.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guard.checkAllowsSession(usuario))
        .isInstanceOfSatisfying(
            ForbiddenException.class, ex -> assertThat(ex.getMessage()).isEqualTo("Conta banida."));
  }

  @Test
  void suspendedUserIsRejectedWithReasonAndDeadline() {
    Usuario usuario = usuarioWithStatus(UserStatus.SUSPENDED);
    Instant until = Instant.parse("2026-09-01T00:00:00Z");
    Sancao sancao = sancaoWith("Reincidência de cancelamentos", until);
    when(sancaoRepository.findBlockingSanction(usuario.getId())).thenReturn(Optional.of(sancao));

    assertThatThrownBy(() -> guard.checkAllowsSession(usuario))
        .isInstanceOfSatisfying(
            ForbiddenException.class,
            ex -> {
              assertThat(ex.code()).isEqualTo("ACCOUNT_SUSPENDED");
              assertThat(ex.details()).containsEntry("reason", "Reincidência de cancelamentos");
              assertThat(ex.details()).containsEntry("until", until.toString());
            });
  }

  private Usuario usuarioWithStatus(UserStatus status) {
    Usuario usuario = mock(Usuario.class);
    when(usuario.getId()).thenReturn(UUID.randomUUID());
    when(usuario.getStatus()).thenReturn(status);
    return usuario;
  }

  private Sancao sancaoWith(String motivo, Instant fim) {
    Sancao sancao = mock(Sancao.class);
    when(sancao.getMotivo()).thenReturn(motivo);
    when(sancao.getFim()).thenReturn(fim);
    return sancao;
  }
}
