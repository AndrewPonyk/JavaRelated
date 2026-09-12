# Runtime image for the HL7v2 Ingestion service.
# Expects the jar to be built first: `mvn -DskipTests package` (run from backend/).
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY hl7v2-ingestion-service/target/*.jar app.jar
USER app
EXPOSE 8082 2575
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
