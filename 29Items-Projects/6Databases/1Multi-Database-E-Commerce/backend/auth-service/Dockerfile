# Build context = backend/ reactor root:
#   docker build -f auth-service/Dockerfile -t shopflow/auth-service ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
RUN mvn -q -pl common,auth-service -am dependency:go-offline
COPY common/src common/src
COPY auth-service/src auth-service/src
RUN mvn -q -pl common,auth-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/auth-service/target/*.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
