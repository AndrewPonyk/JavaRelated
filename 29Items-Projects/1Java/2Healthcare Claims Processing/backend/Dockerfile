# Healthcare Claims Processing - Backend Dockerfile
# Multi-stage build for Quarkus native image

# Stage 1: Build
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:23.1-java21 AS build

USER root
WORKDIR /code

# Copy Maven files first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source code
COPY src ./src

# Build native executable
RUN ./mvnw package -Pnative -DskipTests -q

# Stage 2: Runtime
FROM quay.io/quarkus/quarkus-micro-image:2.0

WORKDIR /work

# Copy native executable
COPY --from=build /code/target/*-runner /work/application

# Set permissions
RUN chmod 775 /work /work/application

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/q/health/ready || exit 1

# Run as non-root user
USER 1001

# Start application
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
