package io.backend.notifications.controller;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      org.springframework.http.HttpHeaders headers,
      org.springframework.http.HttpStatusCode status,
      org.springframework.web.context.request.WebRequest request) {

    ProblemDetail problem =
        createProblemDetail(ex, status, "Validation failed", null, null, request);

    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", fieldErrors);

    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Object> handleResponseStatus(
      ResponseStatusException ex, org.springframework.web.context.request.WebRequest request) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    ProblemDetail problem = createProblemDetail(ex, status, ex.getReason(), null, null, request);
    return handleExceptionInternal(
        ex, problem, org.springframework.http.HttpHeaders.EMPTY, status, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> handleGenericException(
      Exception ex, org.springframework.web.context.request.WebRequest request) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        createProblemDetail(
            ex,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
            null,
            null,
            request);
    return handleExceptionInternal(
        ex,
        problem,
        org.springframework.http.HttpHeaders.EMPTY,
        HttpStatus.INTERNAL_SERVER_ERROR,
        request);
  }
}
