package com.uaiou.credits.repository;

import com.uaiou.credits.entity.Plano;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoRepository extends JpaRepository<Plano, UUID> {}
