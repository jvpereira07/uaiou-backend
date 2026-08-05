package com.uaiou.delivery.repository;

import com.uaiou.delivery.entity.AjusteLancamentoFrete;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AjusteLancamentoFreteRepository
    extends JpaRepository<AjusteLancamentoFrete, UUID> {}
