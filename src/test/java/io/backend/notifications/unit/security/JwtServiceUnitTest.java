package io.backend.notifications.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.backend.notifications.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests for JwtService. No Spring context — ReflectionTestUtils to inject @Value fields. */
class JwtServiceUnitTest {

  // A valid base64-encoded key (≥ 32 bytes = 256 bits for HS256)
  private static final String TEST_SECRET = "dGVzdHNlY3JldGtleWZvcnRlc3Rpbmd3aXRoZW5vdWdoYnl0ZXM=";
  private static final long EXPIRATION_MS = 86400000L;

  private JwtService jwtService;
  private UserDetails userDetails;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
    ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);

    userDetails = new User("test@example.com", "hashedpassword", List.of());
  }

  @Test
  void shouldGenerateValidToken() {
    String token = jwtService.generateToken(userDetails);

    assertThat(token).isNotBlank();
    // JWT structure: header.payload.signature — 3 dots
    assertThat(token.split("\\.")).hasSize(3);
  }

  @Test
  void shouldExtractEmailFromToken() {
    String token = jwtService.generateToken(userDetails);

    String extractedEmail = jwtService.extractEmail(token);

    assertThat(extractedEmail).isEqualTo("test@example.com");
  }

  @Test
  void shouldReturnTrueForValidToken() {
    String token = jwtService.generateToken(userDetails);

    boolean valid = jwtService.isTokenValid(token, userDetails);

    assertThat(valid).isTrue();
  }

  @Test
  void shouldReturnFalseForTokenWithWrongEmail() {
    String token = jwtService.generateToken(userDetails);

    UserDetails otherUser = new User("other@example.com", "hash", List.of());
    boolean valid = jwtService.isTokenValid(token, otherUser);

    assertThat(valid).isFalse();
  }

  @Test
  void shouldReturnFalseForExpiredToken() {
    // Set expiration to -1 ms so token is immediately expired
    ReflectionTestUtils.setField(jwtService, "expirationMs", -1L);

    String token = jwtService.generateToken(userDetails);

    // isTokenValid checks expiration internally
    boolean valid = jwtService.isTokenValid(token, userDetails);

    assertThat(valid).isFalse();
  }

  @Test
  void shouldThrowOnTamperedToken() {
    String token = jwtService.generateToken(userDetails);
    String tampered = token.substring(0, token.length() - 5) + "XXXXX";

    assertThatThrownBy(() -> jwtService.extractEmail(tampered)).isInstanceOf(Exception.class);
  }
}
