# Build context = backend/ reactor root:
#   docker build -f catalog-service/Dockerfile -t shopflow/catalog-service ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/
COPY catalog-service/pom.xml catalog-service/
RUN mvn -q -pl common,catalog-service -am dependency:go-offline
COPY common/src common/src
COPY catalog-service/src catalog-service/src
RUN mvn -q -pl common,catalog-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/catalog-service/target/*.jar app.jar
USER app
EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
