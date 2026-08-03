package com.uaiou.delivery.repository;

import com.uaiou.delivery.entity.Otp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpRepository extends JpaRepository<Otp, UUID> {

  Optional<Otp> findByPedidoId(UUID pedidoId);

  boolean existsByPedidoId(UUID pedidoId);
}
