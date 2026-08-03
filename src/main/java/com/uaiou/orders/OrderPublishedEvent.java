package com.uaiou.orders;

import java.util.UUID;

/**
 * RF-11.10 — publicado APÓS o commit da transação de criação. O fan-out não pode acontecer dentro
 * da transação: notificar sobre um pedido cuja criação ainda pode reverter produziria entregador
 * correndo atrás de pedido que nunca existiu.
 */
public record OrderPublishedEvent(UUID pedidoId) {}
