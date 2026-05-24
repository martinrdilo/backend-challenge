package io.backend.notifications.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.config.RateLimitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RateLimitConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(EnableProperties.class)
          .withPropertyValues(
              "rate-limit.login-attempts-per-minute=5",
              "rate-limit.register-attempts-per-minute=3",
              "rate-limit.window-minutes=1");

  @EnableConfigurationProperties(RateLimitConfig.class)
  static class EnableProperties {}

  @Test
  @DisplayName("should bind login-attempts-per-minute from configuration")
  void shouldBindLoginAttemptsPerMinute() {
    contextRunner.run(
        ctx -> {
          RateLimitConfig config = ctx.getBean(RateLimitConfig.class);
          assertThat(config.loginAttemptsPerMinute()).isEqualTo(5);
        });
  }

  @Test
  @DisplayName("should bind register-attempts-per-minute from configuration")
  void shouldBindRegisterAttemptsPerMinute() {
    contextRunner.run(
        ctx -> {
          RateLimitConfig config = ctx.getBean(RateLimitConfig.class);
          assertThat(config.registerAttemptsPerMinute()).isEqualTo(3);
        });
  }

  @Test
  @DisplayName("should bind window-minutes from configuration")
  void shouldBindWindowMinutes() {
    contextRunner.run(
        ctx -> {
          RateLimitConfig config = ctx.getBean(RateLimitConfig.class);
          assertThat(config.windowMinutes()).isEqualTo(1);
        });
  }
}
