package com.uaiou.tickets.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.tickets.TicketStatus;
import com.uaiou.tickets.dto.AddMessageRequest;
import com.uaiou.tickets.dto.CreateTicketRequest;
import com.uaiou.tickets.dto.ResolveTicketRequest;
import com.uaiou.tickets.dto.TicketDetail;
import com.uaiou.tickets.dto.TicketSummary;
import com.uaiou.tickets.service.SupportTicketService;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /support-tickets*} (api/suporte.md) — RF-21.1 a RF-21.6. */
@RestController
@RequestMapping("/support-tickets")
public class SupportTicketsController {

  private final SupportTicketService supportTicketService;
  private final CurrentUserHolder currentUserHolder;

  public SupportTicketsController(
      SupportTicketService supportTicketService, CurrentUserHolder currentUserHolder) {
    this.supportTicketService = supportTicketService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TicketDetail create(@Valid @RequestBody CreateTicketRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    return supportTicketService.create(user.userId(), request);
  }

  @GetMapping
  public List<TicketSummary> list(
      @RequestParam(required = false) TicketStatus status,
      @RequestParam(required = false) Role authorType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() == Role.ADMIN) {
      return supportTicketService.listForAdmin(status, authorType, from, to);
    }
    return supportTicketService.listForAuthor(user.userId());
  }

  @GetMapping("/{id}")
  public TicketDetail get(@PathVariable UUID id) {
    AuthenticatedUser user = currentUserHolder.require();
    return supportTicketService.get(user.userId(), user.role() == Role.ADMIN, id);
  }

  @PostMapping("/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public TicketDetail addMessage(
      @PathVariable UUID id, @Valid @RequestBody AddMessageRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    return supportTicketService.addMessage(user.userId(), user.role() == Role.ADMIN, id, request);
  }

  @PutMapping("/{id}/resolution")
  public TicketDetail resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolveTicketRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.ADMIN) {
      throw new com.uaiou.shared.error.ForbiddenException(
          "ADMIN_ONLY", "Só o admin encerra um chamado.");
    }
    return supportTicketService.resolve(user.userId(), id, request);
  }
}
