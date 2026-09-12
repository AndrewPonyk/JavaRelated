# Multi-stage build — deployed to ECS Fargate by cd-deploy.yml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 rtap
USER rtap
WORKDIR /app
COPY --from=build /build/target/analytics-api-*.jar app.jar
EXPOSE 8080
# Container-aware heap; config via environment (SSM/Secrets in AWS — TECH-NOTES §3.4)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
# HEALTHCHECK intentionally omitted: ECS/ALB target-group health checks hit /actuator/health
