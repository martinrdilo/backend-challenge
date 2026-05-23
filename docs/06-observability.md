# Observability & Monitoring

This document covers the observability layer added in v0.3.0: structured logging, Micrometer metrics, correlation IDs, and custom health checks.

## Structured Logging

Logback is configured with `logstash-logback-encoder` for JSON structured output. The configuration is profile-aware:

| Profile | Console | File |
|---------|---------|------|
| `dev` / `default` | Human-readable text with MDC bracket | None |
| All others | JSON (`LoggingEventCompositeJsonEncoder`) | JSON rotating file at `logs/structured.log` |

### JSON Log Format

```json
{
  "timestamp": "2026-05-23T14:30:00.123Z",
  "level": "INFO",
  "logger": "i.b.n.listener.NotificationDispatchListener",
  "thread": "dispatch-1",
  "message": "Notification dispatched successfully",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "42",
  "channel": "EMAIL",
  "notificationId": "157"
}
```

### File Rotation

The file appender uses `SizeAndTimeBasedRollingPolicy`:
- Rotates daily and when files reach 10 MB
- Keeps 30 days of history
- Caps total storage at 1 GB

## Correlation IDs

Every HTTP request receives a correlation ID that flows through the entire request lifecycle — including across `@Async` boundaries.

### Filter Chain

`CorrelationIdFilter` is the **first filter** in Spring Security's chain:

```
CorrelationIdFilter → RateLimitFilter → JwtAuthFilter → UsernamePasswordAuthenticationFilter
```

This guarantees every downstream filter, controller, and service has access to the correlation ID via MDC.

### Behavior

- If the request includes `X-Correlation-Id: <uuid>`, the filter uses it as-is (passthrough for distributed tracing)
- If the header is absent, the filter generates a new UUID
- MDC is **always cleared in a `finally` block**, even when exceptions occur

### Async Propagation

A `TaskDecorator` registered on the dispatch `ThreadPoolTaskExecutor` captures MDC context at task submission and restores it on the worker thread:

```java
taskExecutor.setTaskDecorator(runnable -> {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return () -> {
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
        try {
            runnable.run();
        } finally {
            MDC.clear();
        }
    };
});
```

This is a one-time configuration in `AsyncConfig` — the `NotificationDispatchListener` requires **zero code changes** to benefit from it.

## Micrometer Metrics

All metrics are exposed via `/actuator/metrics` and require authentication (`/actuator/health` is the only public actuator endpoint).

### Dispatch Counters

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `notification.dispatched` | Counter | `channel`, `outcome` | Incremented on every dispatch completion. `outcome` is `sent` or `failed`. |
| `notification.retry.total` | Counter | — | Incremented on every retry attempt by `@Retryable`. |

### Dispatch Timer

| Metric | Type | Description |
|--------|------|-------------|
| `notification.dispatch.duration` | Timer | Wall-clock time from dispatch start to completion (includes retries). |

### Thread Pool Gauges

| Metric | Type | Description |
|--------|------|-------------|
| `executor.queue.remaining` | Gauge | Available capacity in the work queue (max 25 minus current size). 0 = queue full. |
| `executor.active` | Gauge | Currently executing dispatch threads. |
| `executor.pool.size` | Gauge | Current pool size (core=2, max=4). |

### Querying Metrics

```bash
# List all available metrics
curl -H "Authorization: Bearer <token>" /actuator/metrics

# Get dispatched count
curl -H "Authorization: Bearer <token>" /actuator/metrics/notification.dispatched

# Get active thread count
curl -H "Authorization: Bearer <token>" /actuator/metrics/executor.active
```

## Health Checks

All health indicators are aggregated under `/actuator/health` (public, no auth required).

### Custom Indicators

| Component | Key | Logic |
|-----------|-----|-------|
| `DispatchHealthIndicator` | `dispatch` | UP when active threads < max pool size and queue < 90% full. DOWN otherwise. |
| `ExternalApiHealthIndicator` | `externalApi` | UP when `GET {EXTERNAL_API_BASE_URL}/posts/1` returns 2xx or 3xx. DOWN on connection failure or 4xx/5xx. |
| `DataSourceHealthIndicator` | `database` | Built-in Spring Boot Actuator indicator. UP when `SELECT 1` succeeds. |

### Sample Response

```json
{
  "status": "UP",
  "components": {
    "dispatch": {
      "status": "UP",
      "details": {
        "active": 1,
        "corePoolSize": 2,
        "maxPoolSize": 4,
        "queueSize": 0,
        "queueRemaining": 25
      }
    },
    "externalApi": {
      "status": "UP"
    },
    "database": {
      "status": "UP"
    }
  }
}
```

## Configuration Reference

All relevant configuration in `application.yml`:

```yaml
# Graceful shutdown
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

# Actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,metrics

# Log level
logging:
  level:
    io.backend: DEBUG
```

## Integration Tests

The observability layer is tested at multiple levels:

- **Unit**: `CorrelationIdFilterTest`, `DispatchHealthIndicatorTest`, `ExternalApiHealthIndicatorTest`, `AsyncConfigTest` (TaskDecorator), `NotificationDispatchListenerTest` (metrics)
- **Integration**: `CorrelationIdIntegrationTest` (end-to-end MDC propagation through async dispatch using `MdcTestRecorder`), `HealthIntegrationTest` (actuator endpoints, metrics auth, metric accumulation)
- **Total**: 53 new tests across all observability components

> For the full decision record behind these choices, see [`05-technical-decisions.md`](./05-technical-decisions.md).
