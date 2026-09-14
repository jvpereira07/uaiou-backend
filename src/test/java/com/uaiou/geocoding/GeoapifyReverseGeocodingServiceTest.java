package com.uaiou.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.uaiou.geocoding.config.ReverseGeocodingProperties;
import com.uaiou.geocoding.service.GeoapifyReverseGeocodingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

/**
 * Corpos de resposta capturados do provedor real, não inventados — as duas fontes que o Geoapify
 * mistura no Brasil devolvem formatos diferentes, e foi exatamente aí que a primeira versão desta
 * classe errou.
 */
class GeoapifyReverseGeocodingServiceTest {

  /** Origem OpenStreetMap: tem `suburb` e `state_code`. */
  private static final String SAO_PAULO =
      """
      {"results":[{"country":"Brasil","country_code":"br","state":"São Paulo",
      "city":"São Paulo","postcode":"01014-000","suburb":"Sé","street":"Rua Boa Vista",
      "housenumber":"254","state_code":"SP","result_type":"amenity"}]}
      """;

  /**
   * Origem OpenAddresses: <strong>sem</strong> `suburb` e <strong>sem</strong> `state_code`, com
   * `state` trazendo a região e `district` repetindo o nome da cidade.
   */
  private static final String SANTA_RITA =
      """
      {"results":[{"country":"Brasil","country_code":"br","county":"Minas Gerais",
      "postcode":"37536-042","state":"Sudeste","district":"Santa Rita Do Sapucaí",
      "city":"Santa Rita do Sapucaí","county_code":"MG",
      "street":"Rua Coronel Antonio Moreira Da Costa","housenumber":"177"}]}
      """;

  @Test
  void readsSuburbAndStateCodeFromAnOpenStreetMapResult() {
    Resultado resultado = responder(SAO_PAULO);

    assertThat(resultado.endereco()).isPresent();
    ReverseGeocodingService.Address endereco = resultado.endereco().orElseThrow();
    assertThat(endereco.postalCode()).isEqualTo("01014000");
    assertThat(endereco.street()).isEqualTo("Rua Boa Vista");
    assertThat(endereco.district()).isEqualTo("Sé");
    assertThat(endereco.city()).isEqualTo("São Paulo");
    assertThat(endereco.state()).isEqualTo("SP");
  }

  /**
   * O caso que motivou a troca do provedor: aqui o Nominatim devolvia o CEP geral da cidade
   * (37540-000), que o ViaCEP recusa, e o formulário ficava vazio.
   */
  @Test
  void fallsBackToCountyCodeAndDropsADistrictThatIsJustTheCityName() {
    Resultado resultado = responder(SANTA_RITA);

    ReverseGeocodingService.Address endereco = resultado.endereco().orElseThrow();
    assertThat(endereco.postalCode()).isEqualTo("37536042");
    assertThat(endereco.state()).isEqualTo("MG");
    // `district` era o nome da cidade — repassá-lo encheria "Bairro" com "Santa Rita Do Sapucaí".
    assertThat(endereco.district()).isNull();
    assertThat(endereco.city()).isEqualTo("Santa Rita do Sapucaí");
  }

  @Test
  void aPostalCodeThatIsNotEightDigitsIsDiscardedRatherThanPassedOn() {
    Resultado resultado =
        responder(
            """
            {"results":[{"postcode":"1234","city":"Lisboa","street":"Rua Augusta"}]}
            """);

    ReverseGeocodingService.Address endereco = resultado.endereco().orElseThrow();
    assertThat(endereco.postalCode()).isNull();
    assertThat(endereco.city()).isEqualTo("Lisboa");
  }

  @Test
  void anEmptyResultListMeansNoAddress() {
    assertThat(responder("{\"results\":[]}").endereco()).isEmpty();
  }

  /** RF-25.10 aplicado aqui: provedor fora do ar é "não sei", nunca exceção para o chamador. */
  @Test
  void aProviderFailureBecomesAnEmptyAnswerNotAnException() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/v1/geocode/reverse")))
        .andRespond(withServerError());

    GeoapifyReverseGeocodingService service =
        new GeoapifyReverseGeocodingService(builder.build(), propriedades());

    assertThat(service.reverse(new BigDecimal("-23.5"), new BigDecimal("-46.6"))).isEmpty();
  }

  @Test
  void sendsTheCoordinateAndTheKeyToTheProvider() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(MockRestRequestMatchers.queryParam("lat", "-23.545513"))
        .andExpect(MockRestRequestMatchers.queryParam("lon", "-46.632791"))
        .andExpect(MockRestRequestMatchers.queryParam("apiKey", "chave-de-teste"))
        .andExpect(MockRestRequestMatchers.queryParam("lang", "pt"))
        .andRespond(withSuccess(SAO_PAULO, MediaType.APPLICATION_JSON));

    new GeoapifyReverseGeocodingService(builder.build(), propriedades())
        .reverse(new BigDecimal("-23.545513"), new BigDecimal("-46.632791"));

    server.verify();
  }

  private Resultado responder(String corpo) {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(org.hamcrest.Matchers.containsString("/v1/geocode/reverse")))
        .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

    GeoapifyReverseGeocodingService service =
        new GeoapifyReverseGeocodingService(builder.build(), propriedades());
    return new Resultado(
        service.reverse(new BigDecimal("-23.545513"), new BigDecimal("-46.632791")));
  }

  private ReverseGeocodingProperties propriedades() {
    return new ReverseGeocodingProperties(
        "chave-de-teste", "https://api.geoapify.com", "pt", Duration.ofSeconds(3));
  }

  private record Resultado(Optional<ReverseGeocodingService.Address> endereco) {}
}
