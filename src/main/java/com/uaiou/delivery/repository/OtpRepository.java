package com.uaiou.delivery.repository;

import com.uaiou.delivery.entity.Otp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpRepository extends JpaRepository<Otp, UUID> {

  Optional<Otp> findByPedidoId(UUID pedidoId);

  boolean existsByPedidoId(UUID pedidoId);

  /** RF-26.28 — desistência descarta o código; o próximo aceite gera outro. */
  void deleteByPedidoId(UUID pedidoId);
}
