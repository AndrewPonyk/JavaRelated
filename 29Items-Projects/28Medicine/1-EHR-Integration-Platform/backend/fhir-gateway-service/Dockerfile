# Runtime image for the FHIR Gateway service.
# Expects the jar to be built first: `mvn -DskipTests package` (run from backend/).
# (CI builds with cached deps, then packages — far faster than compiling in-image.)
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY fhir-gateway-service/target/*.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
