# Multi-stage build. Build context MUST be the backend/ reactor root so the
# parent POM and the `common` module are available:
#   docker build -f order-service/Dockerfile -t shopflow/order-service ./backend
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy poms first for dependency-layer caching.
COPY pom.xml ./
COPY common/pom.xml common/
COPY order-service/pom.xml order-service/
RUN mvn -q -pl common,order-service -am dependency:go-offline
# Then sources.
COPY common/src common/src
COPY order-service/src order-service/src
RUN mvn -q -pl common,order-service -am clean package -DskipTests

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
# Run as non-root.
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/order-service/target/*.jar app.jar
USER app
EXPOSE 8083
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
