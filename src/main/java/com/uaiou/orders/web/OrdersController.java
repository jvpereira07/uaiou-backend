package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.orders.service.AssignmentService;
import com.uaiou.orders.service.OrderService;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/pedidos.md} cobertos por T-11. */
@RestController
@RequestMapping("/orders")
public class OrdersController {

  private static final String STATUS_PUBLISHED = "published";
  private static final List<String> STATUS_DO_ENTREGADOR = List.of("accepted", "finalized");

  private final OrderService orderService;
  private final AssignmentService assignmentService;
  private final CurrentUserHolder currentUserHolder;

  public OrdersController(
      OrderService orderService,
      AssignmentService assignmentService,
      CurrentUserHolder currentUserHolder) {
    this.assignmentService = assignmentService;
    this.orderService = orderService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Só estabelecimentos publicam pedidos.");
    }
    return orderService.create(user.userId(), request);
  }

  /**
   * RF-11.6 — {@code status} não escolhe o ESCOPO, só qual recorte dentro do escopo do papel: o
   * estabelecimento sempre vê os próprios, o entregador sempre vê a vitrine elegível ou os seus.
   * Nenhum valor de query alcança pedido de terceiro.
   */
  @GetMapping
  public OrderListResponse list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    AuthenticatedUser user = currentUserHolder.require();
    PagingRequest paging = PagingRequest.of(page, perPage);

    if (user.role() == Role.MERCHANT) {
      return orderService.listForMerchant(user.userId(), paging, "/api/v1/orders");
    }
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException(
          "ROLE_NOT_SUPPORTED", "Esta rota é de estabelecimento ou entregador.");
    }

    String recorte = status == null ? STATUS_PUBLISHED : status;
    if (STATUS_PUBLISHED.equals(recorte)) {
      return orderService.listPublishedForCourier(
          user.userId(), paging, "/api/v1/orders?status=published");
    }
    if (STATUS_DO_ENTREGADOR.contains(recorte)) {
      return orderService.listAssignedToCourier(
          user.userId(), paging, "/api/v1/orders?status=" + recorte);
    }
    throw new BadRequestException(
        "UNSUPPORTED_STATUS", "\"" + recorte + "\" não é um filtro válido para este papel.");
  }

  /**
   * RF-13.1 — aceitar é <strong>criar a {@code assignment}</strong> do pedido, não um verbo na URL.
   * Corpo vazio: a identidade do entregador vem do token, nunca do payload.
   *
   * <p>{@code Idempotency-Key} é aceito por compatibilidade com o contrato, mas a idempotência aqui
   * não depende dele — ver {@link com.uaiou.orders.service.AssignmentService#accept}.
   */
  @PostMapping("/{id}/assignment")
  @ResponseStatus(HttpStatus.CREATED)
  public AssignmentResponse accept(
      @PathVariable UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Só entregadores aceitam pedidos.");
    }
    return assignmentService.accept(user.userId(), id);
  }

  @GetMapping("/{id}")
  public OrderResponse get(@PathVariable UUID id) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT && user.role() != Role.COURIER) {
      throw new ForbiddenException(
          "ROLE_NOT_SUPPORTED", "Esta rota é de estabelecimento ou entregador.");
    }
    return orderService.get(user.userId(), user.role() == Role.COURIER, id);
  }
}
