package com.uaiou.credits.repository;

import com.uaiou.credits.entity.CarteiraCreditos;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarteiraCreditosRepository extends JpaRepository<CarteiraCreditos, UUID> {

  /**
   * RF-09.11 — {@code SELECT ... FOR UPDATE}: a linha fica bloqueada até o fim da transação, então
   * duas publicações concorrentes do mesmo estabelecimento nunca leem o mesmo saldo "antes" do
   * débito uma da outra. O {@code CHECK (saldo_creditos >= 0)} do banco é só a última linha de
   * defesa, não o mecanismo principal.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CarteiraCreditos c where c.estabelecimentoId = :estabelecimentoId")
  Optional<CarteiraCreditos> findByIdForUpdate(@Param("estabelecimentoId") UUID estabelecimentoId);
}
