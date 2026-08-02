package com.uaiou.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentValidatorTest {

  private final EnvironmentValidator validator = new EnvironmentValidator();

  @Test
  void failsFastNamingEveryMissingVariable() {
    MockEnvironment environment =
        new MockEnvironment().withProperty("DB_HOST", "localhost").withProperty("DB_PORT", "5432");

    assertThatThrownBy(() -> validator.validate(environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DB_NAME")
        .hasMessageContaining("DB_USER")
        .hasMessageContaining("DB_PASSWORD")
        .hasMessageNotContaining("DB_HOST");
  }

  @Test
  void passesWhenAllRequiredVariablesArePresent() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("DB_HOST", "localhost")
            .withProperty("DB_PORT", "5432")
            .withProperty("DB_NAME", "uaiou")
            .withProperty("DB_USER", "uaiou")
            .withProperty("DB_PASSWORD", "secret");

    assertThatNoException().isThrownBy(() -> validator.validate(environment));
  }

  @Test
  void blankValueCountsAsMissing() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("DB_HOST", "   ")
            .withProperty("DB_PORT", "5432")
            .withProperty("DB_NAME", "uaiou")
            .withProperty("DB_USER", "uaiou")
            .withProperty("DB_PASSWORD", "secret");

    assertThatThrownBy(() -> validator.validate(environment)).hasMessageContaining("DB_HOST");
  }

  @Test
  void skipsValidationEntirelyWhenTestProfileIsActive() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");

    assertThatNoException().isThrownBy(() -> validator.validate(environment));
    assertThat(environment.getProperty("DB_HOST")).isNull();
  }
}
