package io.backend.notifications.unit.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.fixture.entity.UserBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserBuilder")
class UserBuilderTest {

  @Test
  @DisplayName("should include phone and deviceToken in RegisterRequest when set")
  void shouldIncludePhoneAndDeviceTokenInRegisterRequest() {
    var reg =
        UserBuilder.aUser()
            .withPhone("+5491112345678")
            .withDeviceToken("tok_abc123")
            .buildRegisterRequest();

    assertThat(reg.phone()).isEqualTo("+5491112345678");
    assertThat(reg.deviceToken()).isEqualTo("tok_abc123");
  }

  @Test
  @DisplayName("should default phone to +5491100000000 in RegisterRequest")
  void shouldDefaultPhoneInRegisterRequest() {
    var reg = UserBuilder.aUser().buildRegisterRequest();

    assertThat(reg.phone()).isEqualTo("+5491100000000");
    assertThat(reg.deviceToken()).isNull();
  }

  @Test
  @DisplayName("should default phone to +5491100000000 in built entity")
  void shouldDefaultPhoneInBuiltEntity() {
    var user = UserBuilder.aUser().build();

    assertThat(user.getPhone()).isEqualTo("+5491100000000");
    assertThat(user.getDeviceToken()).isNull();
  }
}
