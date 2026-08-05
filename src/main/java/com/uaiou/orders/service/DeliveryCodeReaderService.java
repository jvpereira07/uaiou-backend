package com.uaiou.orders.service;

import com.uaiou.delivery.DeliveryCodeStatus;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.DeliveryCodeCipher;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.GoneException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /orders/{id}/delivery/code} — RF-15.11. Só o estabelecimento dono; entregador (mesmo
 * atribuído) e admin não têm rota para ver o código em claro (RN-08.3/RN-08.9).
 *
 * <p>GET que escreve auditoria de propósito: é a contrapartida de expor o código — a sequência
 * "estabelecimento leu → entregador acertou de primeira depois de N erros" é padrão de conluio
 * detectável só porque esta marca existe.
 */
@Service
public class DeliveryCodeReaderService {

  private final PedidoRepository pedidoRepository;
  private final OtpRepository otpRepository;
  private final DeliveryCodeCipher cipher;

  public DeliveryCodeReaderService(
      PedidoRepository pedidoRepository, OtpRepository otpRepository, DeliveryCodeCipher cipher) {
    this.pedidoRepository = pedidoRepository;
    this.otpRepository = otpRepository;
    this.cipher = cipher;
  }

  @Transactional
  public DeliveryCodeResponse read(UUID estabelecimentoId, UUID pedidoId) {
    Pedido pedido =
        pedidoRepository
            .findById(pedidoId)
            .filter(candidato -> candidato.pertenceAoEstabelecimento(estabelecimentoId))
            .orElseThrow(this::notFound);
    Otp otp = otpRepository.findByPedidoId(pedidoId).orElseThrow(this::notFound);

    if (pedido.getStatus() == OrderStatus.FINALIZED
        || pedido.getStatus() == OrderStatus.CONTESTABLE_FINALIZED) {
      throw new GoneException(
          "DELIVERY_CODE_GONE", "Este pedido já foi finalizado; o código não é mais legível.");
    }

    otp.registrarLeitura();
    otpRepository.save(otp);

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put(
        "dispatches", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery/code-dispatches"));

    return new DeliveryCodeResponse(
        pedidoId,
        otp.getStatus() == DeliveryCodeStatus.BLOCKED
            ? null
            : cipher.decifrar(otp.getCodigoCifrado()),
        otp.getStatus(),
        otp.getExpiraEm(),
        new DeliveryCodeResponse.Audit(otp.getLeituras(), otp.getUltimaLeituraEm()),
        links);
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
