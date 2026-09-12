# Multi-stage Docker build for Vert.x Java 17 Backend

# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app
COPY pom.xml .
# Cache dependencies
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*-fat.jar app.jar

ENV HTTP_PORT=8080
ENV HTTP_HOST=0.0.0.0
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
