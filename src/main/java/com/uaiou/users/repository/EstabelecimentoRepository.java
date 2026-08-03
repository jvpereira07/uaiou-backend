package com.uaiou.users.repository;

import com.uaiou.users.entity.Estabelecimento;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, UUID> {

  boolean existsByCnpj(String cnpj);

  /**
   * RF-11.4 — serializa a criação de pedidos DO MESMO estabelecimento para o número sequencial não
   * colidir sob concorrência. Bloquear a linha do estabelecimento (e não a tabela de pedidos) é o
   * que permite "ler o maior número e somar 1" com segurança: dois pedidos simultâneos da mesma
   * loja viram uma fila, enquanto lojas diferentes seguem em paralelo.
   *
   * <p>A UNIQUE {@code uk_pedido_numero_por_estabelecimento} (V5) continua sendo a última linha de
   * defesa, do mesmo jeito que o {@code CHECK >= 0} é para a carteira de créditos (T-09).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Estabelecimento e where e.usuarioId = :usuarioId")
  Optional<Estabelecimento> findByIdForUpdate(@Param("usuarioId") UUID usuarioId);
}
