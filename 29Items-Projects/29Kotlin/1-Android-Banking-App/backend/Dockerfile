# Slim runtime image. The fat jar is built on the host first:
#   ./gradlew :backend:buildFatJar
# then: docker compose build (root docker-compose.yml).
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY build/libs/*-all.jar app.jar
# Migrations as real files: Flyway's fat-jar classpath scanning can reject valid
# names (observed on 10.10.0); compose points FLYWAY_LOCATIONS here instead.
COPY src/main/resources/db/migration /app/migration

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
