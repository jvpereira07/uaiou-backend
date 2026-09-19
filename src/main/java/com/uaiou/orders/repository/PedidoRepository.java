package com.uaiou.orders.repository;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository
    extends JpaRepository<Pedido, UUID>, JpaSpecificationExecutor<Pedido> {

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

  /**
   * RF-13.2/RF-13.7 — {@code SELECT ... FOR UPDATE}: a corrida pelo aceite é a REGRA, não a exceção
   * (vários entregadores veem o mesmo pedido). Quem chega depois espera o lock e então lê o estado
   * já atualizado — é o lock que garante "um entregador por pedido" (RN-01.2), não uma verificação
   * otimista, que aqui só produziria retry no cliente numa disputa frequente.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Pedido p where p.id = :id")
  Optional<Pedido> findByIdForUpdate(@Param("id") UUID id);

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
          // RF-26.27 — quem desistiu do pedido não o vê mais na vitrine.
          + " and not exists ("
          + "   select 1 from DesistenciaPedido d"
          + "   where d.pedidoId = p.id and d.entregadorId = :entregadorId)"
          + " order by p.criadoEm desc")
  List<Pedido> findNaCaixaDelimitadora(
      @Param("status") List<OrderStatus> status,
      @Param("latMin") BigDecimal latMin,
      @Param("latMax") BigDecimal latMax,
      @Param("longMin") BigDecimal longMin,
      @Param("longMax") BigDecimal longMax,
      @Param("entregadorId") UUID entregadorId);

  /**
   * RF-26.1 — só ids: quem decide é a leitura travada logo depois. Carregar a entidade aqui a
   * deixaria no contexto de persistência com o estado anterior ao lock.
   */
  @Query(
      "select p.id from Pedido p where p.entregadorId = :entregadorId"
          + " and p.status = :status and p.chegouEm is null")
  List<UUID> idsAguardandoChegada(
      @Param("entregadorId") UUID entregadorId, @Param("status") OrderStatus status);

  /** RF-17.6 — janela de contestação vencida, para o job de consolidação. */
  List<Pedido> findByStatusAndFinalizadoEmBefore(OrderStatus status, java.time.Instant limite);

  /** RF-19.2/RF-19.8 — entregas finalizadas de cada lado, insumo de "pendentes de avaliar". */
  List<Pedido> findByEstabelecimentoIdAndStatusIn(UUID estabelecimentoId, List<OrderStatus> status);

  List<Pedido> findByEntregadorIdAndStatusIn(UUID entregadorId, List<OrderStatus> status);

  /** RF-19.5 — job do padrão positivo: finalizadas com janela de avaliação vencida. */
  List<Pedido> findByStatusInAndFinalizadoEmBefore(
      List<OrderStatus> status, java.time.Instant limite);

  /** RF-22.2/RF-22.3 — recorte por período para as estatísticas de cada papel. */
  List<Pedido> findByEntregadorIdAndCriadoEmBetween(
      UUID entregadorId, java.time.Instant de, java.time.Instant ate);

  List<Pedido> findByEstabelecimentoIdAndCriadoEmBetween(
      UUID estabelecimentoId, java.time.Instant de, java.time.Instant ate);

  // ---- timeouts (V26): só ids, pelo mesmo motivo de idsAguardandoChegada — quem decide é a
  // leitura travada.

  @Query("select p.id from Pedido p where p.status in :status and p.criadoEm < :limite")
  List<UUID> idsCriadosAntesDe(
      @Param("status") List<OrderStatus> status, @Param("limite") java.time.Instant limite);

  @Query("select p.id from Pedido p where p.status in :status and p.aceitoEm < :limite")
  List<UUID> idsAceitosAntesDe(
      @Param("status") List<OrderStatus> status, @Param("limite") java.time.Instant limite);

  /**
   * Sinalização é uma vez por pedido: sem o {@code not exists}, o job sinalizaria a cada minuto.
   */
  @Query(
      "select p.id from Pedido p where p.status in :status and p.coletadoEm < :limite"
          + " and not exists ("
          + "   select 1 from OcorrenciaTimeout o where o.pedidoId = p.id and o.chave = :chave)")
  List<UUID> idsColetadosAntesDeSemOcorrencia(
      @Param("status") List<OrderStatus> status,
      @Param("limite") java.time.Instant limite,
      @Param("chave") String chave);

  /** Painel admin — contagem por status para o resumo do histórico. */
  @Query("select p.status, count(p) from Pedido p group by p.status")
  List<Object[]> contarPorStatus();
}
