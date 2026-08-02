package com.uaiou.users.repository;

import com.uaiou.users.entity.Entregador;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntregadorRepository extends JpaRepository<Entregador, UUID> {

  boolean existsByCpf(String cpf);
}
