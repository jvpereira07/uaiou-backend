package com.uaiou.geocoding.web;

import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.geocoding.ReverseGeocodingService;
import com.uaiou.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /geocoding/reverse?lat=..&lng=..} — o salto coordenada → CEP dos seletores de endereço
 * no mapa (app e web).
 *
 * <p>Exige sessão como qualquer outro recurso: a chamada gasta cota da nossa conta no provedor, e
 * endpoint aberto de geocodificação é uma conta de terceiro que qualquer um pode consumir.
 *
 * <p>Endereço ausente é <strong>200 com campos nulos</strong>, não 404: "não sei o endereço deste
 * ponto" é resposta legítima, não recurso inexistente — e o cliente já tem o que precisa (a
 * coordenada) para seguir sem ele.
 */
@RestController
@RequestMapping("/geocoding/reverse")
public class ReverseGeocodingController {

  private static final BigDecimal LAT_MIN = new BigDecimal("-90");
  private static final BigDecimal LAT_MAX = new BigDecimal("90");
  private static final BigDecimal LNG_MIN = new BigDecimal("-180");
  private static final BigDecimal LNG_MAX = new BigDecimal("180");

  private final ReverseGeocodingService reverseGeocodingService;
  private final CurrentUserHolder currentUserHolder;

  public ReverseGeocodingController(
      ReverseGeocodingService reverseGeocodingService, CurrentUserHolder currentUserHolder) {
    this.reverseGeocodingService = reverseGeocodingService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public ReverseGeocodingResponse reverse(
      @RequestParam BigDecimal lat, @RequestParam BigDecimal lng) {
    currentUserHolder.require();
    validar(lat, lng);

    return reverseGeocodingService
        .reverse(lat, lng)
        .map(ReverseGeocodingResponse::from)
        .orElseGet(ReverseGeocodingResponse::vazia);
  }

  private void validar(BigDecimal lat, BigDecimal lng) {
    if (foraDoIntervalo(lat, LAT_MIN, LAT_MAX) || foraDoIntervalo(lng, LNG_MIN, LNG_MAX)) {
      throw new BusinessRuleException(
          "INVALID_COORDINATES",
          "As coordenadas enviadas não correspondem a um ponto válido no globo.",
          "RN-08.1");
    }
  }

  private boolean foraDoIntervalo(BigDecimal valor, BigDecimal min, BigDecimal max) {
    return valor.compareTo(min) < 0 || valor.compareTo(max) > 0;
  }

  /**
   * Nomes em inglês como o resto do contrato HTTP (ver {@code api/pedidos.md}); a tradução para
   * rua/bairro/cidade acontece no cliente.
   */
  public record ReverseGeocodingResponse(
      String postalCode, String street, String district, String city, String state) {

    static ReverseGeocodingResponse from(ReverseGeocodingService.Address endereco) {
      return new ReverseGeocodingResponse(
          endereco.postalCode(),
          endereco.street(),
          endereco.district(),
          endereco.city(),
          endereco.state());
    }

    static ReverseGeocodingResponse vazia() {
      return new ReverseGeocodingResponse(null, null, null, null, null);
    }
  }
}
