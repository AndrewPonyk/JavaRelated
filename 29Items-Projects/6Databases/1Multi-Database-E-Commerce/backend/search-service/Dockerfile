# Build context = backend/ reactor root:
#   docker build -f search-service/Dockerfile -t shopflow/search-service ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/
COPY search-service/pom.xml search-service/
RUN mvn -q -pl common,search-service -am dependency:go-offline
COPY common/src common/src
COPY search-service/src search-service/src
RUN mvn -q -pl common,search-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/search-service/target/*.jar app.jar
USER app
EXPOSE 8084
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
