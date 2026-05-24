package io.backend.notifications.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.dto.UserResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserResponse")
class UserResponseTest {

  @Test
  @DisplayName("should include phone and deviceToken in response")
  void shouldIncludePhoneAndDeviceToken() {
    var now = LocalDateTime.now();
    var resp = new UserResponse(1L, "user1", "u1@test.com", now, "+5491112345678", "tok_abc123");
    assertThat(resp.phone()).isEqualTo("+5491112345678");
    assertThat(resp.deviceToken()).isEqualTo("tok_abc123");
  }

  @Test
  @DisplayName("should accept empty phone string (migration default)")
  void shouldAcceptEmptyPhoneFromMigrationDefault() {
    var now = LocalDateTime.now();
    var resp = new UserResponse(1L, "user1", "u1@test.com", now, "", null);
    assertThat(resp.phone()).isEmpty();
    assertThat(resp.deviceToken()).isNull();
  }
}
