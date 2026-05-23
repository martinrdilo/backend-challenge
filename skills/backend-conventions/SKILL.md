---
name: backend-conventions
description: "Trigger: working on backend-challenge, Spring Boot, notifications API, Java 21. Enforce project conventions for this Spring Boot 3.5 / JWT / Flyway / Strategy-pattern codebase."
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## Activation Contract

Load this skill when editing Java source, tests, configs, or migrations in this project. Apply all Hard Rules unconditionally.

## Hard Rules

1. **Constructor injection only.** No `@Autowired` field injection. Declare dependencies as `private final` fields set via constructor parameters.
2. **Screaming architecture — domain packages.** New classes go under `io.backend.notifications.{domainConcept}` (e.g. `controller`, `service`, `service/channel`, `entity`, `repository`, `security`, `client`, `dto`, `config`, `enums`, `exception`). Never group by layer across domains.
3. **Strategy pattern for channels.** Adding a notification channel: implement `ChannelSender`, add `@Component`, return the `Channel` enum from `getChannel()`. `ChannelDispatcher` auto-discovers via `List<ChannelSender>` constructor injection. Never `if/else` on channel type.
4. **Stateless JWT auth.** `SessionCreationPolicy.STATELESS`. No server-side sessions. Extract user identity from `SecurityContextHolder` — never from `HttpSession`.
5. **IDOR protection — 404 vs 403.** Resource lookup: throw 404 if entity not found, 403 if found but belongs to another user. Use `findOwnNotification()` pattern: find → verify ownership via `SecurityContextHolder.getContext().getAuthentication().getName()`.
6. **Flyway-only schema changes.** `ddl-auto: validate` in production, `none` in test profile. Never let Hibernate create/drop tables in prod. All schema changes = new migration `V{N}__description.sql` under `src/main/resources/db/migration/`.
7. **Immutable channel on update.** `NotificationUpdateRequest` excludes `channel`. Once dispatched, channel is read-only. PATCH/PUT must not accept channel field.
8. **Integration tests use real JWT.** No `@WithMockUser`. Auth flow: `registerAndLogin(builder)` → obtain Bearer token → set `Authorization: Bearer {token}` header. Extend `AbstractIntegrationTest`.
9. **Test directory split.** Unit tests → `src/test/java/.../unit/`. Integration tests → `.../integration/`. Test fixtures → `.../fixture/entity/` (builder pattern) and `.../fixture/wiremock/` (`WireMockHelper`). External API mocking uses WireMock.
10. **RestClient, not RestTemplate.** External HTTP calls use `RestClient.Builder` injected via `RestClientConfig`, configured per-client in constructors. Never introduce `RestTemplate`.

## Decision Gates

| Situation | Do |
|---|---|
| Create a new domain concept | Add top-level package under `io.backend.notifications.{concept}` with controller, service, repository, entity, dto |
| Add a notification channel | Implement `ChannelSender` + `@Component`, return enum from `getChannel()` |
| Schema change required | Add Flyway migration SQL file, never modify ddl-auto |
| Authenticate in a test | Use `registerAndLogin(UserBuilder.aUser())`, add Bearer header to `WebTestClient` |
| Call an external API | Inject `RestClient.Builder`, build per-client in constructor |
| Protect a resource endpoint | Find entity → verify `user.email == authenticatedEmail` → 404 if missing, 403 if wrong owner |

## Execution Steps

1. Before generating code, identify which domain package the change lands in.
2. Verify no `@Autowired` field injection; use constructor injection for all dependencies.
3. For new endpoints, add ownership checks matching the `findOwnNotification` pattern.
4. For schema changes, write a Flyway migration — never rely on Hibernate DDL.
5. For tests, decide unit vs integration placement; use `UserBuilder`/`NotificationBuilder` fixtures and `WireMockHelper` for external APIs.
6. Run `./gradlew test` before considering work done.

## Output Contract

All generated code must: use constructor injection, follow domain-package layout, enforce IDOR ownership checks, use RestClient for HTTP, provide Flyway migrations for schema changes, and place tests in the correct unit/integration directory.

## References

- See `references/docs.md` for links to detailed project documentation.