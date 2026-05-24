package io.backend.notifications.unit.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link User} entity — validates nullable phone and deviceToken fields. */
@DisplayName("User entity")
class UserTest {

  @Test
  @DisplayName("should get and set phone number")
  void shouldGetAndSetPhone() {
    User user = new User();
    user.setPhone("+5491112345678");
    assertThat(user.getPhone()).isEqualTo("+5491112345678");
  }

  @Test
  @DisplayName("should return null phone by default")
  void shouldReturnNullPhoneByDefault() {
    User user = new User();
    assertThat(user.getPhone()).isNull();
  }

  @Test
  @DisplayName("should get and set device token")
  void shouldGetAndSetDeviceToken() {
    User user = new User();
    user.setDeviceToken("tok_abc123");
    assertThat(user.getDeviceToken()).isEqualTo("tok_abc123");
  }

  @Test
  @DisplayName("should return null device token by default")
  void shouldReturnNullDeviceTokenByDefault() {
    User user = new User();
    assertThat(user.getDeviceToken()).isNull();
  }
}
