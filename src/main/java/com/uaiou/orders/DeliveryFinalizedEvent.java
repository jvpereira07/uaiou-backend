package com.uaiou.orders;

import com.uaiou.delivery.FinalizationType;
import java.util.UUID;

/** RF-15.12/RF-17.7 — efeitos pós-commit da finalização, por código ou contestável. */
public record DeliveryFinalizedEvent(UUID pedidoId, FinalizationType tipo) {}
