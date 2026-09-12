# Build context = backend/ reactor root:
#   docker build -f recommendation-service/Dockerfile -t shopflow/recommendation-service ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/
COPY recommendation-service/pom.xml recommendation-service/
RUN mvn -q -pl common,recommendation-service -am dependency:go-offline
COPY common/src common/src
COPY recommendation-service/src recommendation-service/src
RUN mvn -q -pl common,recommendation-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/recommendation-service/target/*.jar app.jar
USER app
EXPOSE 8085
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
