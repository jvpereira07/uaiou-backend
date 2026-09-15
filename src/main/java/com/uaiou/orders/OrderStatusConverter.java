package com.uaiou.orders;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

  @Override
  public String convertToDatabaseColumn(OrderStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case CREATED -> "criado";
      case PUBLISHED -> "publicado";
      case IN_NEGOTIATION -> "em_negociacao";
      case ACCEPTED -> "aceito";
      case PICKED_UP -> "coletado";
      case FINALIZED -> "finalizado";
      case CONTESTABLE_FINALIZED -> "finalizado_contestavel";
      case CANCELLED -> "cancelado";
    };
  }

  @Override
  public OrderStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "criado" -> OrderStatus.CREATED;
      case "publicado" -> OrderStatus.PUBLISHED;
      case "em_negociacao" -> OrderStatus.IN_NEGOTIATION;
      case "aceito" -> OrderStatus.ACCEPTED;
      case "coletado" -> OrderStatus.PICKED_UP;
      case "finalizado" -> OrderStatus.FINALIZED;
      case "finalizado_contestavel" -> OrderStatus.CONTESTABLE_FINALIZED;
      case "cancelado" -> OrderStatus.CANCELLED;
      default -> throw new IllegalStateException("Status de pedido desconhecido: " + dbData);
    };
  }
}
