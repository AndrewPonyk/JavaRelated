# syntax=docker/dockerfile:1
# Multi-stage build: Maven + JDK 21 to build, JRE-only layered runtime, non-root user.
# Build context = services/order-service (see ci.yml).

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Warm the dependency cache first so code changes don't re-download the world.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dcheckstyle.skip dependency:go-offline

COPY src ./src
# Tests and lint run in CI, not inside the image build (single source of truth).
# The jar is exploded into Spring Boot layers so that dependency layers are
# reused across releases — only the application layer changes on a code change.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests -Dcheckstyle.skip package \
 && cp target/*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime

# Non-root, no shell profile, fixed UID for K8s runAsNonRoot/securityContext.
RUN groupadd --system --gid 10001 app \
 && useradd  --system --uid 10001 --gid app --home /app app
WORKDIR /app

# Least-volatile layers first → maximum registry/layer cache reuse.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

# Container-aware JVM sizing; UTC everywhere; G1 for predictable pauses.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Duser.timezone=UTC"

EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
