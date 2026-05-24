package io.backend.notifications.unit.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.backend.notifications.controller.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("GlobalExceptionHandler")
@SuppressWarnings("unused")
class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @RestController
  @SuppressWarnings("unused")
  static class TestController {

    @PostMapping("/test/validation-error")
    ResponseEntity<String> validationError(@Valid @RequestBody TestRequest request) {
      return ResponseEntity.ok("should not reach here");
    }

    @GetMapping("/test/not-found")
    ResponseEntity<String> notFound() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @GetMapping("/test/conflict")
    ResponseEntity<String> conflict() {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Resource already exists");
    }

    @GetMapping("/test/server-error")
    ResponseEntity<String> serverError() {
      throw new RuntimeException("Something went wrong internally");
    }
  }

  record TestRequest(@NotBlank String name, @Email String email) {}

  @Nested
  @DisplayName("Validation error (400)")
  class ValidationErrors {

    @Test
    @DisplayName("should return ProblemDetail with errors map for invalid request body")
    void shouldReturnProblemDetailWithErrors() throws Exception {
      mockMvc
          .perform(
              post("/test/validation-error")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                    {"name":"","email":"invalid"}
                                    """))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Bad Request"))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.detail").exists())
          .andExpect(jsonPath("$.instance").exists())
          .andExpect(jsonPath("$.errors").exists())
          .andExpect(jsonPath("$.errors.name").exists())
          .andExpect(jsonPath("$.errors.email").exists());
    }
  }

  @Nested
  @DisplayName("Not found (404)")
  class NotFoundErrors {

    @Test
    @DisplayName("should return ProblemDetail for 404 ResponseStatusException")
    void shouldReturnProblemDetailForNotFound() throws Exception {
      mockMvc
          .perform(get("/test/not-found"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Not Found"))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.detail").value("Resource not found"))
          .andExpect(jsonPath("$.instance").exists());
    }
  }

  @Nested
  @DisplayName("Conflict (409)")
  class ConflictErrors {

    @Test
    @DisplayName("should return ProblemDetail for 409 ResponseStatusException")
    void shouldReturnProblemDetailForConflict() throws Exception {
      mockMvc
          .perform(get("/test/conflict"))
          .andExpect(status().isConflict())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Conflict"))
          .andExpect(jsonPath("$.status").value(409))
          .andExpect(jsonPath("$.detail").value("Resource already exists"))
          .andExpect(jsonPath("$.instance").exists());
    }
  }

  @Nested
  @DisplayName("Server error (500)")
  class ServerErrors {

    @Test
    @DisplayName("should return ProblemDetail without stack trace for unhandled exception")
    void shouldReturnProblemDetailForGenericException() throws Exception {
      mockMvc
          .perform(get("/test/server-error"))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").exists())
          .andExpect(jsonPath("$.title").value("Internal Server Error"))
          .andExpect(jsonPath("$.status").value(500))
          .andExpect(jsonPath("$.detail").exists())
          .andExpect(jsonPath("$.instance").exists())
          .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
  }
}
