package com.uaiou.feira.repository;

import com.uaiou.feira.entity.FeiraBonusConquista;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeiraBonusConquistaRepository extends JpaRepository<FeiraBonusConquista, UUID> {

  List<FeiraBonusConquista> findByEntregadorId(UUID entregadorId);

  List<FeiraBonusConquista> findByBonusIdOrderByConquistadoEmAsc(UUID bonusId);

  List<FeiraBonusConquista> findAllByOrderByConquistadoEmAsc();
}
