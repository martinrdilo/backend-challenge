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

## 9. Spring Retry para Channel Dispatch con Backoff Exponencial

El `@Retryable` se aplica en `ChannelDispatcher.dispatch()` en lugar de en cada `ChannelSender` individual porque la anotación requiere una invocación cross-bean para que el proxy de AOP de Spring intercepte el método. Colocarla en el `ChannelDispatcher` centraliza la política de reintento, evita duplicar lógica en cada implementación de canal (DRY) y asegura que cualquier falla transitoria en cualquier sender sea manejada de forma uniforme. Se excluye explícitamente `IllegalStateException` porque una sola clase cubre tanto errores de configuración ("no sender found") como errores de validación ("null email/token"), los cuales son fallas deterministas que no se resuelven con reintentos. Los parámetros de backoff exponencial se configuran con `maxAttempts=3`, `delay=1000ms` y `multiplier=2.0`, produciendo reintentos a los 1s, 2s y 4s después del intento inicial.

No se define un método `@Recover` porque el bloque `catch` existente en `NotificationService` ya actualiza el estado de la notificación a `FAILED`, lo cual preserva el Single Responsibility Principle al evitar inyectar `NotificationRepository` dentro de `ChannelDispatcher`. Las expresiones SpEL `#{...}` en la anotación resuelven valores desde `application.yml`, permitiendo ajustar `maxAttempts` y `delay` por entorno sin recompilar. Para testing, una `@TestConfiguration` provee un `ChannelSender` mock anotado con `@Primary` y el perfil de test sobreescribe el delay a `0ms`, logrando ejecuciones en sub-milisegundos. La estrategia bloquea el thread HTTP hasta ~7s en el peor caso, lo cual es aceptable para el endpoint de creación de notificaciones; la evolución hacia dispatch asincrónico queda como trabajo futuro.

---

## 10. Desacoplamiento Asincrónico con Spring Events + @Async

El endpoint `POST /notifications` ahora devuelve la respuesta HTTP inmediatamente con estado `PENDING`, mientras que el envío real por canal se ejecuta de forma asincrónica en un thread separado. Esto se implementa con `ApplicationEventPublisher`, `@EventListener` y `@Async`.

### Arquitectura

1. `NotificationService.createNotification()` guarda la notificación en la base de datos y publica un `NotificationCreatedEvent`.
2. `NotificationDispatchListener` escucha el evento con `@EventListener` y `@Async`, delegando a `ChannelDispatcher.dispatch()`.
3. El listener corre en su propia transacción con `@Transactional(propagation = REQUIRES_NEW)`, independiente de la transacción del publisher.

### Por qué ApplicationEventPublisher + @EventListener + @Async en lugar de @Async en el service method

- **Separación de responsabilidades**: el service se ocupa de la persistencia; el listener se ocupa del envío. Se pueden testear de forma independiente.
- **Testabilidad**: en tests unitarios se puede mockear el `ApplicationEventPublisher` y verificar que se publica el evento correcto, sin necesidad de ejecutar lógica asincrónica real.
- **Extensibilidad**: otros componentes pueden escuchar `NotificationCreatedEvent` sin modificar el service (ej. métricas, audit logging).
- **Desacoplamiento**: el service no conoce la existencia del dispatcher ni de los senders.

### Por qué REQUIRES_NEW en el listener

El listener necesita su propia transacción porque:
- La transacción del publisher ya está committeada cuando el evento se publica (dentro del mismo método, antes del `return`).
- Si el envío por canal falla, el listener actualiza el estado de la notificación a `FAILED`. Con `REQUIRES_NEW`, esta actualización ocurre en una transacción separada, evitando que un rollback del envío afecte el estado persistido.
- Sin propagación `REQUIRES_NEW`, el listener compartiría (o heredaría) la transacción del publisher, lo cual generaría problemas de sincronización en contexto asincrónico.

### Por qué ThreadPoolTaskExecutor (bounded) en lugar de SimpleAsyncTaskExecutor

- **`SimpleAsyncTaskExecutor`**: crea un nuevo thread por cada tarea. Bajo carga alta, esto agota los recursos del JVM y del sistema operativo (OOM, thread exhaustion).
- **`ThreadPoolTaskExecutor`**: usa un pool acotado con cola de espera. Recursos controlados, rechazo predecible.

Configuración del pool:

| Propiedad | Valor |
|-----------|-------|
| Core pool size | 2 |
| Max pool size | 4 |
| Queue capacity | 25 |
| Thread name prefix | `notification-dispatch-` |

Estos números son conservadores para el scope del challenge, pero demuestran la decisión consciente de usar recursos acotados en lugar de threads ilimitados.

### Cambio de contrato en POST /notifications

Antes: el endpoint bloqueaba el thread HTTP hasta que el envío simulado terminaba y devolvía `SENT` o `FAILED`.

Ahora: el endpoint devuelve inmediatamente `PENDING`. El cliente debe hacer polling a `GET /notifications/{id}` para conocer el estado final (`SENT`, `FAILED`).

### Estrategia de testing

- **Unit tests**: se mockea `ApplicationEventPublisher` y se verifica que `createNotification()` publica el evento con los datos correctos. El listener se testea en isolation mockeando `ChannelDispatcher`.
- **Integration tests**: se usa **Awaitility** para esperar condiciones asincrónicas (ej. "el estado de la notificación pasa de `PENDING` a `SENT` en menos de 5 segundos"). Esto evita `Thread.sleep()` rígidos y hace los tests deterministas.
- **No se usa `@Async` en integration tests para el listener**: se confía en el `ThreadPoolTaskExecutor` real y se espera con Awaitility hasta que el thread de dispatch completa su trabajo.
