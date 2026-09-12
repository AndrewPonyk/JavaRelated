# Build context = backend/ reactor root:
#   docker build -f realtime-service/Dockerfile -t shopflow/realtime-service ./backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY common/pom.xml common/
COPY realtime-service/pom.xml realtime-service/
RUN mvn -q -pl common,realtime-service -am dependency:go-offline
COPY common/src common/src
COPY realtime-service/src realtime-service/src
RUN mvn -q -pl common,realtime-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /workspace/realtime-service/target/*.jar app.jar
USER app
EXPOSE 8086
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
