# Build context = backend/ reactor root:
#   docker build -f api-gateway/Dockerfile -t shopflow/api-gateway ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
RUN mvn -q -pl api-gateway dependency:go-offline
COPY api-gateway/src api-gateway/src
RUN mvn -q -pl api-gateway clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/api-gateway/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
