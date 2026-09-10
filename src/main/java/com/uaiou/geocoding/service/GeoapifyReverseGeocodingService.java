package com.uaiou.geocoding.service;

import com.uaiou.geocoding.ReverseGeocodingService;
import com.uaiou.geocoding.config.ReverseGeocodingProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Implementação Geoapify de {@link ReverseGeocodingService} — mesmo provedor já usado em rotas
 * (T-25), pela mesma chave e sob a mesma licença derivada do OpenStreetMap.
 *
 * <p>Devolve apenas o que o formulário sabe preencher. O resto da resposta do provedor (confiança,
 * hierarquia administrativa, bounding box) é descartado de propósito: guardar campo que ninguém lê
 * só cria contrato para manter.
 */
public class GeoapifyReverseGeocodingService implements ReverseGeocodingService {

  private static final Logger log =
      LoggerFactory.getLogger(GeoapifyReverseGeocodingService.class);

  private final RestClient restClient;
  private final ReverseGeocodingProperties properties;

  public GeoapifyReverseGeocodingService(
      RestClient restClient, ReverseGeocodingProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public Optional<Address> reverse(BigDecimal lat, BigDecimal lng) {
    try {
      GeoapifyResponse resposta =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v1/geocode/reverse")
                          .queryParam("lat", lat)
                          .queryParam("lon", lng)
                          .queryParam("format", "json")
                          .queryParam("lang", properties.language())
                          .queryParam("apiKey", properties.apiKey())
                          .build())
              .retrieve()
              .body(GeoapifyResponse.class);

      return converter(resposta);
    } catch (RuntimeException e) {
      // Não propaga: o contrato da interface é "não sei", nunca erro. Fica o registro para quem
      // for investigar cota estourada ou chave revogada — sem a chave no texto do log.
      log.warn("Geocodificação inversa indisponível: {}", e.toString());
      return Optional.empty();
    }
  }

  private Optional<Address> converter(GeoapifyResponse resposta) {
    if (resposta == null || resposta.results() == null || resposta.results().isEmpty()) {
      return Optional.empty();
    }
    Result primeiro = resposta.results().getFirst();

    Address endereco =
        new Address(
            apenasDigitos(primeiro.postcode()),
            emBranco(primeiro.street()),
            bairro(primeiro),
            emBranco(primeiro.city()),
            estado(primeiro));

    // Um resultado sem nenhum campo aproveitável é o mesmo que resultado nenhum — poupa o cliente
    // de distinguir "veio vazio" de "não veio".
    boolean vazio =
        endereco.postalCode() == null
            && endereco.street() == null
            && endereco.district() == null
            && endereco.city() == null;
    return vazio ? Optional.empty() : Optional.of(endereco);
  }

  /**
   * O provedor nomeia o bairro conforme a fonte que cobre aquele ponto: {@code suburb} quando vem
   * do OpenStreetMap, {@code district} quando vem do OpenAddresses.
   *
   * <p>O descarte de {@code district} igual a {@code city} não é zelo teórico: em Santa Rita do
   * Sapucaí a resposta real traz {@code district: "Santa Rita Do Sapucaí"} — o nome da cidade
   * ocupando o campo de bairro. Repassar isso encheria "Bairro" com a cidade no formulário.
   */
  private String bairro(Result resultado) {
    String suburb = emBranco(resultado.suburb());
    if (suburb != null) {
      return suburb;
    }
    String district = emBranco(resultado.district());
    String city = emBranco(resultado.city());
    if (district == null || district.equalsIgnoreCase(city)) {
      return null;
    }
    return district;
  }

  /**
   * {@code state_code} é a sigla da UF, mas só vem no resultado de origem OpenStreetMap. No de
   * origem OpenAddresses o campo some e {@code state} traz a <em>região</em> ("Sudeste") — é
   * {@code county_code} que carrega a sigla ali. Sem este segundo caminho, endereço de cidade
   * pequena voltaria sempre sem UF.
   */
  private String estado(Result resultado) {
    String sigla = emBranco(resultado.stateCode());
    return sigla != null ? sigla : emBranco(resultado.countyCode());
  }

  private String apenasDigitos(String valor) {
    if (valor == null) {
      return null;
    }
    String digitos = valor.replaceAll("\\D", "");
    // CEP brasileiro tem oito dígitos. Qualquer outra coisa é endereço de fora da praça ou lixo do
    // provedor — devolver pela metade faria o cliente consultar os Correios com CEP inválido.
    return digitos.length() == 8 ? digitos : null;
  }

  private String emBranco(String valor) {
    if (valor == null) {
      return null;
    }
    String limpo = valor.trim();
    return limpo.isEmpty() ? null : limpo;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record GeoapifyResponse(List<Result> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Result(
      String postcode,
      String street,
      String suburb,
      String district,
      String city,
      @JsonProperty("state_code") String stateCode,
      @JsonProperty("county_code") String countyCode) {}
}
