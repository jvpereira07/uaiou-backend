package com.uaiou.notifications.repository;

import com.uaiou.notifications.entity.Dispositivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoRepository extends JpaRepository<Dispositivo, UUID> {

  Optional<Dispositivo> findByPushToken(String pushToken);

  List<Dispositivo> findByUsuarioId(UUID usuarioId);

  Optional<Dispositivo> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
