package com.uaiou.feira.repository;

import com.uaiou.feira.entity.FeiraBonus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeiraBonusRepository extends JpaRepository<FeiraBonus, UUID> {

  List<FeiraBonus> findAllByOrderByMetaAscCriadoEmAsc();
}
