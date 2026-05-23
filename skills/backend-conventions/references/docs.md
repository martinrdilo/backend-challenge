# Backend-Conventions References

Links to detailed project documentation and source files that back each convention.

## Source Files (normative)

| Convention | Source |
|---|---|
| Constructor injection | `src/main/java/io/backend/notifications/service/NotificationService.java` |
| Strategy pattern — channels | `src/main/java/io/backend/notifications/service/channel/ChannelSender.java`, `ChannelDispatcher.java` |
| Stateless JWT | `src/main/java/io/backend/notifications/security/SecurityConfig.java` |
| IDOR protection (404 vs 403) | `src/main/java/io/backend/notifications/service/NotificationService.java` (findOwnNotification) |
| Flyway-only schema | `src/main/resources/application.yml` (ddl-auto), `src/main/resources/db/migration/` |
| Immutable channel on PUT | `src/main/java/io/backend/notifications/dto/NotificationUpdateRequest.java` |
| Integration test JWT flow | `src/test/java/io/backend/notifications/integration/base/AbstractIntegrationTest.java` |
| Test directory split | `src/test/java/io/backend/notifications/unit/`, `.../integration/`, `.../fixture/` |
| RestClient config | `src/main/java/io/backend/notifications/config/RestClientConfig.java`, `.../client/ExternalMediaClient.java` |
| WireMock helper | `src/test/java/io/backend/notifications/fixture/wiremock/WireMockHelper.java` |
| Test fixture builders | `src/test/java/io/backend/notifications/fixture/entity/UserBuilder.java`, `NotificationBuilder.java` |

## Project Documentation

- `README.md` — project overview and setup instructions
- `docs/` — additional project documentation (if present)
- `docker-compose.yml` — local PostgreSQL + app setup
- `docker-compose.test.yml` — test environment setup