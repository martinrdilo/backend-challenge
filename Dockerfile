FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew.bat ./
RUN chmod +x gradlew
COPY gradle gradle
COPY build.gradle settings.gradle gradle.properties ./
RUN ./gradlew dependencies --no-daemon
COPY src src

FROM builder AS api-builder
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=api-builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
