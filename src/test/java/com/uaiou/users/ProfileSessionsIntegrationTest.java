package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-04.6 — critério de aceite 6 de T-04. */
class ProfileSessionsIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void deleteMeSessionsRevokesEveryRefreshTokenAcrossAllDevices() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse firstDevice = login(user);
    SessionResponse secondDevice = loginPassword(user.login(), user.password(), user.role());

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(firstDevice.accessToken());
    ResponseEntity<Void> response =
        restTemplate.exchange(
            baseUrl("/me/sessions"), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<ErrorResponse> firstRefreshAttempt = refresh(firstDevice.refreshToken());
    ResponseEntity<ErrorResponse> secondRefreshAttempt = refresh(secondDevice.refreshToken());

    assertThat(firstRefreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(secondRefreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void deleteMeSessionsWithoutATokenIsRejected() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me/sessions"), HttpMethod.DELETE, null, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private ResponseEntity<ErrorResponse> refresh(String refreshToken) {
    SessionRequest request = new SessionRequest("refresh", null, null, null, refreshToken, null);
    return restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);
  }
}
