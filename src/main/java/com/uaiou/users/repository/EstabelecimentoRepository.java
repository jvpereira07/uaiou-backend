package com.uaiou.users.repository;

import com.uaiou.users.entity.Estabelecimento;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, UUID> {

  boolean existsByCnpj(String cnpj);
}
