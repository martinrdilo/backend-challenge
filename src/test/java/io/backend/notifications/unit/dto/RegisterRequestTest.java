package io.backend.notifications.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.backend.notifications.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegisterRequest")
class RegisterRequestTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  @DisplayName("should accept valid registration request with phone")
  void shouldAcceptValidRequestWithPhone() {
    var req = new RegisterRequest("user1", "u1@test.com", "password123", "+5491112345678", null);
    assertThat(req.phone()).isEqualTo("+5491112345678");
    assertThat(req.deviceToken()).isNull();
  }

  @Test
  @DisplayName("should reject registration request when phone is null")
  void shouldRejectNullPhone() {
    var req = new RegisterRequest("user1", "u1@test.com", "password123", null, null);

    Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
  }

  @Test
  @DisplayName("should reject registration request when phone is empty")
  void shouldRejectEmptyPhone() {
    var req = new RegisterRequest("user1", "u1@test.com", "password123", "", null);

    Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
  }

  @Test
  @DisplayName("should reject registration request when phone is blank")
  void shouldRejectBlankPhone() {
    var req = new RegisterRequest("user1", "u1@test.com", "password123", "   ", null);

    Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

    assertThat(violations).isNotEmpty();
    assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
  }
}
