# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `POST /notifications/{id}/retry` — retry a FAILED notification (resets to PENDING, re-enters async dispatch pipeline)
- PENDING retries are idempotent (200 OK), SENT returns 409 Conflict
- 13 new tests (5 unit + 8 integration), 173 total

### Documentation
- Updated state diagram and error matrix in `docs/02-channel-sending-and-crud.md`
- Added retry endpoint to README API table

## [0.3.2] - 2026-05-24

### Fixed
- `gradle.properties` not included in Docker build, causing Swagger to show `0.0.1-SNAPSHOT` instead of the actual version

## [0.3.1] - 2026-05-24

### Added
- Spotless plugin with Google Java Format — automatic code formatting for all 69 Java source files
- GitHub Actions deploy workflow — Railway deployment triggered by `v*` release tags

### Fixed
- Railway deploy workflow: added `--service feisty-gratitude` flag to resolve multi-service ambiguity

## [0.3.0] - 2026-05-23

### Added
- Swagger UI now displays the application version from `gradle.properties` via Spring Boot `buildInfo()`

### Fixed
- Unit tests (`RateLimitConfigTest`, `GlobalExceptionHandlerTest`) no longer require a database, fixing CI failures
- `run-tests-ci.sh` — simulates CI environment to catch placeholder resolution issues before pushing

## [0.2.1] - 2026-05-23

### Fixed
- Unit tests (`RateLimitConfigTest`, `GlobalExceptionHandlerTest`) no longer require a database, fixing CI failures when Docker or PostgreSQL env vars are unavailable

### Added
- `run-tests-ci.sh` — simulates CI environment to catch placeholder resolution issues before pushing

## [0.2.0] - 2026-05-23

### Added
- RFC 7807 Problem Details error format (`application/problem+json`) for all error responses
- Bucket4j rate limiting on `POST /auth/login` (5/min) and `POST /auth/register` (3/min) per IP
- Paginated notification search with JPA Specifications (filter by status, channel, date range, text)
- Dev seed data via `CommandLineRunner` — 2 users + 5 notifications, idempotent, `\@Profile("!test")`
- Spring Retry with exponential backoff on `ChannelDispatcher.dispatch()` (3 attempts, 1s/2s/4s)
- Async event-driven dispatch — `POST /notifications` returns `PENDING` immediately, `\@Async` listener handles delivery
- `\@ConfigurationPropertiesScan` for type-safe configuration binding
- Awaitility for async integration test assertions

### Fixed
- Removed dead `getMyNotifications()` method to prevent IDOR bypass
- Made seed data idempotent for notifications via `notificationRepository.count()` guard
- Updated retry tests to call `ChannelDispatcher.dispatch()` directly for async compatibility

### Documentation
- Added sections 9–15 to `docs/05-technical-decisions.md` covering all new architectural decisions
- Renamed `docs/06-technical-decisions.md` → `docs/05-technical-decisions.md` for consecutive numbering
- Updated test count and API contract in README

## [0.1.0] - 2026-05-19

### Added
- JWT authentication with stateless token handling
- Notification system with Strategy pattern (SMS, Push, Email channels)
- Flyway versioned migrations replacing Hibernate ddl-auto
- Phone NOT NULL constraint via Flyway V2 migration
- CRUD endpoints for notifications (PUT/DELETE/GET own)
- Swagger/OpenAPI documentation via springdoc
- JaCoCo code coverage with Coveralls CI upload
- CircleCI test pipeline with Gradle caching
- Docker multi-stage build with docker-compose for dev and test
- Architecture decision documentation in `docs/`

### Fixed
- Proxy-aware Swagger URLs via forwarded headers strategy
- PORT env var for Railway compatibility
- Consolidated duplicate Flyway keys in application.yml
- Input validation and enum binding on POST /notifications

### Changed
- Reorganized README with features, areas to improve, and known issues
- Moved architecture docs from `.docs/` to `docs/` for public visibility
