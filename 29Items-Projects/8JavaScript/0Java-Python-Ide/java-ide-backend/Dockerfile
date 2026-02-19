# Build stage
FROM maven:3.9-eclipse-temurin-17 AS backend-builder

WORKDIR /build

# Copy pom.xml and backend source
COPY pom.xml ./
COPY src ./src

# Build the JAR (static files already in src/main/resources/static/)
RUN mvn clean package -DskipTests -Dskip.frontend=true

# Production stage
FROM eclipse-temurin:17-jdk-alpine

# Install Python
RUN apk add --no-cache python3

WORKDIR /app

# Copy the built JAR
COPY --from=backend-builder /build/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
