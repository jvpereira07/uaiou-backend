package com.uaiou.orders.repository;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

  /**
   * RF-11.4 — maior número já usado por este estabelecimento. Nativa porque {@code numero} é {@code
   * varchar} (o formato legível "0011" é do contrato) e o máximo precisa ser numérico, não
   * lexicográfico: sem o cast, "9" seria maior que "0011".
   *
   * <p>Não serializa nada por si só — quem garante a ausência de colisão é o lock em {@code
   * estabelecimento} tomado antes desta chamada (ver {@code OrderService}).
   */
  @Query(
      value =
          "select coalesce(max(cast(numero as integer)), 0) from pedido where estabelecimento_id = :estabelecimentoId",
      nativeQuery = true)
  int maiorNumeroDoEstabelecimento(@Param("estabelecimentoId") UUID estabelecimentoId);

  Page<Pedido> findByEstabelecimentoIdOrderByCriadoEmDesc(
      UUID estabelecimentoId, Pageable pageable);

  Page<Pedido> findByEntregadorIdAndStatusInOrderByCriadoEmDesc(
      UUID entregadorId, List<OrderStatus> status, Pageable pageable);

  /**
   * RF-11.5 — pré-filtro da vitrine por CAIXA DELIMITADORA (T-02 decidiu caixa em vez de PostGIS):
   * o recorte fino por distância real é feito depois, em memória, porque a caixa é um quadrado e o
   * raio é um círculo. Já exclui os estabelecimentos que bloquearam o entregador (RF-11.5/T-12) —
   * fazer isso no banco evita trazer pedido que seria descartado logo em seguida.
   */
  @Query(
      "select p from Pedido p where p.status in :status"
          + " and p.destLat between :latMin and :latMax"
          + " and p.destLong between :longMin and :longMax"
          + " and not exists ("
          + "   select 1 from Bloqueio b"
          + "   where b.estabelecimentoId = p.estabelecimentoId and b.entregadorId = :entregadorId)"
          + " order by p.criadoEm desc")
  List<Pedido> findNaCaixaDelimitadora(
      @Param("status") List<OrderStatus> status,
      @Param("latMin") BigDecimal latMin,
      @Param("latMax") BigDecimal latMax,
      @Param("longMin") BigDecimal longMin,
      @Param("longMax") BigDecimal longMax,
      @Param("entregadorId") UUID entregadorId);
}
