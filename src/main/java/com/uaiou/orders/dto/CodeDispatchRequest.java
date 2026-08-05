package com.uaiou.orders.dto;

/**
 * {@code POST /orders/{id}/delivery/code-dispatches} (RF-16.4). {@code receiverPhone} corrige um
 * pedido que nasceu sem telefone — quando ausente, é só o registro do ato de repasse.
 */
public record CodeDispatchRequest(String receiverPhone) {}
