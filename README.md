# Notification Management System

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/martinrdilo/notification-management-system/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/martinrdilo/notification-management-system/tree/main)
[![Coverage Status](https://coveralls.io/repos/github/martinrdilo/notification-management-system/badge.svg?branch=main)](https://coveralls.io/github/martinrdilo/notification-management-system?branch=main)

REST API for notification management with JWT authentication and simulated multi-channel delivery (Email, SMS, Push). Built as a take-home technical challenge.

## 🚀 Live Demo

A running instance is deployed on Railway — no setup required:

**[🔗 Open Swagger](https://feisty-gratitude-production-a5d2.up.railway.app/swagger-ui/index.html)**

How to test:
1. **Register**: `POST /auth/register` with a username, email, and password
2. **Login**: `POST /auth/login` with the same credentials — copy the JWT token from the response
3. **Authorize**: Click the 🔒 **Authorize** button in Swagger, paste the token, and click Authorize
4. **Try it out**: all authenticated endpoints are now available — create notifications, list them, update, delete

## Features

- **User registration & login** with JWT authentication (stateless)
- **CRUD for notifications**: create, read, update, delete
- **Multi-channel simulated delivery**: Email, SMS, and Push — each with channel-specific validation and formatting
- **Ownership enforcement**: users can only access their own notifications (IDOR protection)
- **Open/Closed Principle**: adding a new channel requires zero changes to existing code
- **Swagger UI**: interactive API documentation at `/swagger-ui.html`
- **Paginated notification search** with dynamic filtering by status, channel, date range, and text
- **Dev seed data** — idempotent sample users and notifications on startup

## Quick Start

```bash
# 1. Clone the repository
git clone <repo-url>
cd backend-challenge

# 2. Build and start the app + PostgreSQL
./run-app.sh

# Or manually:
docker compose up -d
./gradlew bootRun
```

The API will be available at `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

## API Endpoints

### Authentication (`/auth`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/auth/register` | Register a new user (username, email, password) | No |
| POST | `/auth/login` | Log in, returns JWT | No |

### Notifications (`/notifications`)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/notifications` | List authenticated user's notifications — paginated, supports `?status`, `?channel`, `?createdAfter`, `?createdBefore`, `?search` | Bearer |
| GET | `/notifications/{id}` | Get notification by ID (own only) | Bearer |
| POST | `/notifications` | Create notification (returns `PENDING`, dispatches asynchronously) | Bearer |
| PUT | `/notifications/{id}` | Update notification (title, content, attachmentIds) | Bearer |
| DELETE | `/notifications/{id}` | Delete notification (own only) | Bearer |

### Create Notification — Request

```json
POST /notifications
{
  "title": "System Alert",
  "content": "Server requires scheduled maintenance.",
  "channel": "EMAIL",
  "attachmentIds": [1, 2]
}
```

### Response

```json
{
  "id": 1,
  "title": "System Alert",
  "content": "Server requires scheduled maintenance.",
  "channel": "EMAIL",
  "status": "PENDING",
  "createdAt": "2026-05-10T01:30:00",
  "userId": 1,
  "attachments": [
    { "id": 1, "title": "photo-1", "url": "https://..." }
  ]
}
```

## Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.0 |
| Security | Spring Security + JJWT 0.12.6 (stateless) |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| API Docs | SpringDoc OpenAPI 2.8.5 (Swagger UI) |
| Build | Gradle 9.2.1 |
| Tests | JUnit 5, Testcontainers, WireMock, WebTestClient |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | — | PostgreSQL JDBC URL |
| `DB_USERNAME` | — | Database user |
| `DB_PASSWORD` | — | Database password |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL strategy |
| `JWT_SECRET` | — | Secret key for signing JWT tokens |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiration (24h default) |
| `EXTERNAL_API_BASE_URL` | `https://jsonplaceholder.typicode.com` | External API for photo enrichment |

> See `.env.example` for a ready-to-use template.

## Tests

```bash
# Run all tests with Docker
./run-tests.sh

# Or manually:
./gradlew test
```

**Current coverage**: 160 tests (unit + integration), 0 failures.

- **Unit**: `MockitoExtension`, no Spring context. Each sender and service tested in isolation.
- **Integration**: `Testcontainers` (real PostgreSQL) + `WireMock` (external API) + `WebTestClient` (HTTP).

## Architecture Highlights

### Stateless JWT Authentication

Spring Security with a JWT filter and no server-side session (`SessionCreationPolicy.STATELESS`). Every request carries the token in `Authorization: Bearer <token>`. The `JwtAuthFilter` sets the `SecurityContext` before requests reach controllers. Enables horizontal scaling without sticky sessions.

### Strategy Pattern for Channel Delivery (OCP)

Channel delivery uses the Strategy pattern with Spring's auto-discovery. Each channel is a `@Component` implementing `ChannelSender`, and the `ChannelDispatcher` builds a `Map<Channel, ChannelSender>` for O(1) dispatch. Delivery is simulated (logging only), with each channel applying its own validation and formatting.

**Adding a new channel** (e.g. WhatsApp) requires only two steps with zero changes to existing code:
1. Add `WHATSAPP` to the `Channel` enum
2. Create `WhatsAppChannelSender implements ChannelSender` as a `@Component`

Spring auto-detects it and the dispatcher picks it up — satisfying the Open/Closed Principle.

### Ownership Enforcement (IDOR protection)

Endpoints that access a notification by ID validate it belongs to the authenticated user via `findOwnNotification(id)`. Mismatch returns **403 Forbidden**; not found returns **404**. Prevents Insecure Direct Object Reference attacks.

### Implicit User Derivation

`GET /notifications` derives the user from the `SecurityContext` instead of exposing a `userId` in the URL. Avoids exposing user IDs in the API — the auth context determines which data is returned.

### Constructor Injection

All dependencies use constructor injection (no `@Autowired`). Dependencies are explicit, objects immutable post-construction, and unit testing with Mockito is trivial without a Spring context.

### Immutable Channel on Update

`PUT /notifications/{id}` uses `NotificationUpdateRequest`, a DTO that excludes the `channel` field. Once dispatched through a channel, it can't be changed — there's no business case for "un-sending."

### Search ByCriteria with JPA Specifications

`GET /notifications` uses `JpaSpecificationExecutor` to build composable, type-safe dynamic queries. Every query starts with an IDOR ownership filter (`belongsToUser`), and additional criteria are added only when the corresponding request parameter is present.

Adding a new search criterion requires only a new static factory method in `NotificationSpecification` — zero changes to the controller, service, or existing predicates. This follows the same Open/Closed Principle used in the `ChannelSender` pattern.

### Integration Tests with Real Infrastructure

Integration tests use real PostgreSQL via Testcontainers, WireMock for the external API, and `WebTestClient` for HTTP. The database is never mocked. Catches real SQL dialect differences and constraint issues.

### RFC 7807 Error Format

Error responses follow the IETF RFC 7807 standard (`application/problem+json`). Instead of ad-hoc JSON structures, every error returns a `ProblemDetail` object with `type`, `title`, `status`, and `detail` fields. Field-level validation errors are embedded under `errors`. Clients can parse failures without guessing the response shape, and stack traces are never leaked.

### Auth Rate Limiting

A Bucket4j token-bucket filter protects authentication endpoints from brute-force attacks. The filter runs before `JwtAuthFilter` and enforces per-IP limits: 5 login requests per minute and 3 register requests per minute. Excess requests receive `429 Too Many Requests` with a `Retry-After` header. IP addresses are resolved from `X-Forwarded-For` when present, falling back to the remote address. Storage is in-memory and per-instance.

### Async Event-Driven Dispatch

`POST /notifications` returns immediately with `PENDING` status, decoupling the HTTP response from channel delivery. A Spring `ApplicationEventPublisher` fires a `NotificationCreatedEvent` after persistence, and an `@Async` listener (`NotificationDispatchListener`) handles the actual dispatch on a separate thread with its own transaction (`REQUIRES_NEW`). A bounded `ThreadPoolTaskExecutor` (core=2, max=4, queue=25) prevents resource exhaustion. Clients poll `GET /notifications/{id}` for the final `SENT` or `FAILED` status.

> Full reasoning behind each decision → [`docs/05-technical-decisions.md`](docs/05-technical-decisions.md)

## Documentation

Detailed docs in [`docs/`](docs/):

- [`01-authentication.md`](docs/01-authentication.md) — JWT flow and security configuration
- [`02-channel-sending-and-crud.md`](docs/02-channel-sending-and-crud.md) — Strategy pattern for channel dispatch + CRUD operations
- [`03-testing-infrastructure.md`](docs/03-testing-infrastructure.md) — Testcontainers, WireMock, and test architecture
- [`04-testing-architecture-diagram.md`](docs/04-testing-architecture-diagram.md) — Visual overview of the test setup
- [`05-technical-decisions.md`](docs/05-technical-decisions.md) — Detailed reasoning behind all architectural decisions

## Areas to Improve

Tradeoffs and improvements I'd make with more time:

- **Database performance**: add indexes on `notifications(user_id, status, channel, created_at)` for combined criteria queries
- **Observability**: add structured logging (JSON) and retry metrics for the async dispatch listener
- **Concurrent load testing**: verify thread pool behavior under 10+ simultaneous POST requests

## Versioning

This project follows [Semantic Versioning](https://semver.org/) (MAJOR.MINOR.PATCH). The version is stored in `gradle.properties`:

```properties
appVersion=0.1.0
```

### Release workflow

**During development**: update `CHANGELOG.md` under the `[Unreleased]` section as you merge changes. Do not touch the version number.

**When releasing** (on `main`):

1. **Bump the version** in `gradle.properties` according to SemVer:
   - Bug fix → patch bump (`0.1.0` → `0.1.1`)
   - New feature, backward-compatible → minor bump (`0.1.0` → `0.2.0`)
   - Breaking change → major bump (`0.1.0` → `1.0.0`)

2. **Close the changelog**: rename `[Unreleased]` to `[X.Y.Z] - YYYY-MM-DD` and add a fresh `[Unreleased]` section at the top.

3. **Commit and tag**:
   ```bash
   git add gradle.properties CHANGELOG.md
   git commit -m "release: v0.2.0"
   git tag -a v0.2.0 -m "Release v0.2.0"
   git push origin main --tags
   ```

The Gradle build reads the version automatically:
```groovy
version = project.findProperty('appVersion') ?: '0.0.1-SNAPSHOT'
```

## Known Issues

None at this time.
