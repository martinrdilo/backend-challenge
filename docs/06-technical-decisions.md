# Decisiones Técnicas

Este documento detalla las decisiones arquitectónicas y de diseño tomadas en el proyecto. Para un resumen ejecutivo, ver [Architecture Highlights](../README.md#architecture-highlights) en el README.

---

## 1. Autenticación JWT Stateless

Spring Security está configurado con un filtro JWT sin sesión del lado del servidor (`SessionCreationPolicy.STATELESS`). Cada request incluye el token en el header `Authorization: Bearer <token>`. El `JwtAuthFilter` lo extrae y valida antes de que llegue a los controllers, seteando el `SecurityContext` con el email del usuario.

**Por qué**: las REST APIs no deberían mantener sesiones del lado del servidor. JWT permite escalado horizontal sin sticky sessions. Además, el statelessness simplifica el modelo mental: no hay estado compartido entre requests que se pueda desincronizar.

Para más detalle sobre el flujo completo de autenticación, ver [`01-authentication.md`](./01-authentication.md).

---

## 2. Strategy Pattern para Channel Delivery

La lógica de envío por canal usa el patrón Strategy con auto-descubrimiento de Spring. Una interfaz `ChannelSender` define dos métodos (`send`, `getChannel`) y cada canal tiene su propia implementación anotada con `@Component`. El `ChannelDispatcher` recibe `List<ChannelSender>` vía constructor injection y construye un `Map<Channel, ChannelSender>` para dispatch en O(1).

El envío es **simulado** (solo logging) — el challenge pide simular los pasos, no integrar APIs reales. Cada canal aplica su propia validación y formato:

- **Email**: valida que `user.getEmail()` no sea null, formatea un template, loguea "Email sent to {email}"
- **SMS**: trunca el contenido a 160 caracteres, loguea "SMS sent at {timestamp}"
- **Push**: construye un payload JSON con title y content, loguea "Push notification dispatched: {payload}"

**Por qué**: el requisito clave del challenge es _"la lógica debe estar diseñada de modo que agregar un nuevo canal no requiera modificar la lógica existente"_. Con este diseño, agregar un nuevo canal (ej. WhatsApp) requiere solo dos pasos: (1) agregar `WHATSAPP` al enum `Channel`, (2) crear `WhatsAppChannelSender implements ChannelSender` como `@Component`. Spring lo auto-detecta y el `ChannelDispatcher` lo incorpora sin tocar una sola línea de código existente. Esto satisface el Open/Closed Principle (OCP).

### Cómo agregar un nuevo canal

1. Agregar el nuevo valor al enum `Channel` (`src/main/java/.../enums/Channel.java`)
2. Crear una clase que implemente `ChannelSender`:

```java
@Component
public class WhatsAppChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelSender.class);

    @Override
    public void send(Notification notification) {
        // Validar requisitos específicos del canal
        // Formatear payload
        log.info("WhatsApp message sent to user {}", notification.getUser().getId());
    }

    @Override
    public Channel getChannel() {
        return Channel.WHATSAPP;
    }
}
```

Eso es todo. Spring registra automáticamente el nuevo `@Component` y el `ChannelDispatcher` lo incorpora sin tocar ninguna otra clase.

Para más detalle sobre la implementación del Strategy Pattern, ver [`02-channel-sending-and-crud.md`](./02-channel-sending-and-crud.md).

---

## 3. Ownership Enforcement (protección IDOR)

Todos los endpoints que acceden a una notificación por ID (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`) validan que la notificación pertenezca al usuario autenticado mediante el helper `findOwnNotification(id)`. Este método compara `notification.user.email` con el email del `SecurityContext`. Si no coinciden, devuelve **403 Forbidden**. Si la notificación no existe, devuelve **404 Not Found**.

**Por qué**: los endpoints que exponen IDs en la URL son vulnerables a Insecure Direct Object Reference (IDOR). Sin esta validación, un usuario podría acceder a notificaciones de otro usuario simplemente iterando IDs.

---

## 4. GET /notifications sin userId en la URL

El endpoint para listar notificaciones propias (`GET /notifications`) deriva el usuario del `SecurityContext`, sin requerir un `userId` en la URL. Esto evita exponer IDs de usuario en la API y sigue el principio de que el contexto de autenticación determina los datos devueltos.

**Por qué**: `GET /notifications/user/{userId}` permite que cualquier usuario autenticado especifique un ID arbitrario. Aunque el backend valide ownership, exponer IDs en la URL es mala práctica REST. El endpoint correcto usa el contexto de auth implícitamente.

---

## 5. Constructor Injection

Toda la aplicación usa constructor injection (sin `@Autowired`). Esto hace que las dependencias sean explícitas, los objetos inmutables post-construcción, y el unit testing trivial sin un Spring context.

**Por qué**: es la práctica recomendada por el equipo de Spring desde 2014. Hace que testear con Mockito (`@ExtendWith(MockitoExtension.class)`) sea directo sin necesidad de levantar el contexto completo.

---

## 6. Canal inmutable en PUT

El endpoint de actualización (`PUT /notifications/{id}`) usa `NotificationUpdateRequest`, un DTO que **excluye el campo `channel`**. Una vez que una notificación se crea y se despacha por un canal, ese canal es inmutable.

**Por qué**: cambiar el canal post-dispatch no tiene sentido de negocio (la lógica de Email ya se ejecutó — no se puede "des-enviar"). El DTO de update es explícito sobre qué campos son modificables.

---

## 7. Tests de integración con infraestructura real

Los tests de integración usan PostgreSQL real via Testcontainers y WireMock para simular la API externa de fotos. La base de datos nunca se mockea. `AbstractIntegrationTest` provee infraestructura compartida (contenedor PostgreSQL singleton, WireMock en puerto dinámico, `WebTestClient` para requests HTTP, limpieza FK-safe entre tests).

**Por qué**: testear contra una base de datos real detecta problemas que H2 en modo MySQL/PostgreSQL no detecta (diferencias de dialecto SQL, constraints reales, secuencias de IDs). El overhead de Testcontainers es aceptable por la confianza que da.

Para más detalle sobre la infraestructura de testing, ver:
- [`03-testing-infrastructure.md`](./03-testing-infrastructure.md)
- [`04-testing-architecture-diagram.md`](./04-testing-architecture-diagram.md)

---

## 8. Flyway para Migraciones de Schema

La base de datos usa **Flyway** con PostgreSQL para manejar el schema de forma versionada. Hibernate está configurado con `ddl-auto: validate` en producción y `ddl-auto: none` en tests — nunca modifica la base de datos automáticamente.

### Cómo agregar una migración nueva

1. Crear un archivo en `src/main/resources/db/migration/V{numero}__{descripcion}.sql`
2. Escribir el DDL con SQL puro
3. Al iniciar la app, Flyway aplica las migraciones pendientes en orden y registra el historial en `flyway_schema_history`

### Por qué Flyway y no Liquibase

- **Simplicidad**: SQL puro, sin DSL en XML/YAML/JSON. Cualquier dev lo entiende.
- **Spring Boot auto-config**: cero código de configuración, solo la dependencia en `build.gradle`.
- **Proyecto chico**: 2 entidades, 3 tablas — Liquibase sería sobreingeniería.
- **Fix-forward**: si una migración tiene un error, se corrige con una migración nueva (V3 arregla V2). Flyway open-source no tiene rollback, lo cual fuerza buenas prácticas de no perder datos.

### Por qué `validate` en prod y `none` en tests

- **`validate` en prod**: Hibernate compara entidades con la DB al iniciar. Si hay drift, la app no arranca — falla temprano, con error claro.
- **`none` en tests**: Flyway es la única fuente de verdad del schema. Si la migración está rota, los tests fallan en CI antes de llegar a producción. Con `create-drop`, Hibernate hubiera "arreglado" el bug silenciosamente.

---

## 9. RFC 7807 Problem Details para Manejo de Errores

Todas las respuestas de error de la API usan `ProblemDetail` de Spring Boot 3 en lugar de `Map<String, Object>` construidos manualmente.

**Por qué**:
- **Nativo en Spring Boot 3**: `ProblemDetail` y `ResponseEntityExceptionHandler` vienen en `spring-web` — no requiere dependencias adicionales.
- **Formato estándar IETF**: los clientes pueden parsear errores sin adivinar la estructura del JSON. Los campos `type`, `title`, `status`, y `detail` son predecibles.
- **Errores de campo preservados**: las validaciones de Bean Validation (`@NotBlank`, `@Email`, etc.) se acumulan en una lista bajo el campo `errors` del `ProblemDetail`.
- **Stack trace nunca expuesta**: `ResponseEntityExceptionHandler` maneja la conversión a `ProblemDetail` sin incluir información interna del servidor.

El `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler` y sobrescribe los métodos correspondientes para 400, 401, 403, 404, 409, 429, y 500. El entry point de autenticación en `SecurityConfig` también emite `ProblemDetail` en lugar de JSON hardcodeado.

---

## 10. Bucket4j para Rate Limiting en Auth

La protección de endpoints de autenticación usa Bucket4j con token-bucket en memoria.

**Por qué token-bucket en memoria (y no Redis)**:
- **Simplicidad**: para un challenge técnico, una dependencia adicional (Redis + lettuce) agrega complejidad de infraestructura sin justificación de escala real.
- **Overhead aceptable**: los buckets son objetos livianos (`Map<String, Bucket>`). La sobrecarga de memoria es mínima para el volumen esperado.
- **Tradeoff documentado**: el rate limiting es **por instancia**. En múltiples réplicas, un atacante podría distribuir requests entre instancias. Esto es aceptable para el scope actual y se documenta explícitamente.

**Por qué antes de JwtAuthFilter en la cadena**:
- Una request bloqueada por rate limiting no debería ejecutar lógica de parsing ni validación de JWT. Rechazar lo antes posible reduce carga de CPU y evita side effects.

**Parámetros por defecto**:
- Login: 5 tokens por minuto por IP
- Register: 3 tokens por minuto por IP

**Manejo de X-Forwarded-For**:
- El filtro lee el header `X-Forwarded-For` antes que `getRemoteAddr()` para soportar proxies reversos y load balancers. Si el header está presente, se usa la primera IP de la lista (la más cercana al cliente).

**Retry-After**:
- Cuando un bucket está vacío, la respuesta 429 incluye un header `Retry-After` con los segundos restantes hasta que el próximo token esté disponible. Esto permite a los clientes backoff de forma determinista.
