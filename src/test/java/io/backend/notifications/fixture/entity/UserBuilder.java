package io.backend.notifications.fixture.entity;

import io.backend.notifications.dto.RegisterRequest;
import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.entity.User;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent builder for creating User entities and UserRequest DTOs in tests.
 *
 * <p>Usage: User user = UserBuilder.aUser().build(); User custom =
 * UserBuilder.aUser().withUsername("john").withEmail("john@test.com").build(); UserRequest request
 * = UserBuilder.aUser().buildRequest(); RegisterRequest reg =
 * UserBuilder.aUser().buildRegisterRequest();
 */
public final class UserBuilder {

  private static final AtomicLong COUNTER = new AtomicLong(1);

  private String username;
  private String email;
  private String password = "password123";
  private String phone = "+5491100000000";
  private String deviceToken;

  private UserBuilder() {
    long id = COUNTER.getAndIncrement();
    this.username = "user-" + id;
    this.email = "user-" + id + "@test.com";
  }

  public static UserBuilder aUser() {
    return new UserBuilder();
  }

  public UserBuilder withUsername(String username) {
    this.username = username;
    return this;
  }

  public UserBuilder withEmail(String email) {
    this.email = email;
    return this;
  }

  public UserBuilder withPassword(String password) {
    this.password = password;
    return this;
  }

  public UserBuilder withPhone(String phone) {
    this.phone = phone;
    return this;
  }

  public UserBuilder withDeviceToken(String deviceToken) {
    this.deviceToken = deviceToken;
    return this;
  }

  /**
   * Builds a User entity (without id — JPA will generate it on persist). Sets a default BCrypt-like
   * placeholder for passwordHash to satisfy the not-null DB constraint. Use withPassword() +
   * buildRegisterRequest() for proper auth-aware tests.
   */
  public User build() {
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setPhone(phone);
    // Placeholder hash to satisfy NOT NULL constraint in tests that bypass auth
    user.setPasswordHash("$2a$10$placeholder.hash.for.testing.only");
    return user;
  }

  /** Builds a UserRequest DTO (for controller tests). */
  public UserRequest buildRequest() {
    return new UserRequest(username, email);
  }

  /** Builds a RegisterRequest DTO (for auth controller tests). */
  public RegisterRequest buildRegisterRequest() {
    return new RegisterRequest(username, email, password, phone, deviceToken);
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }
}
