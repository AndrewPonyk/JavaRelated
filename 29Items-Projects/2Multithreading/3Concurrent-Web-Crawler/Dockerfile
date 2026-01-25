# Multi-stage build for minimal production image

# =============================================================================
# Build Stage
# =============================================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy POM first for better layer caching
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests -B

# =============================================================================
# Runtime Stage
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

# Add labels for metadata
LABEL maintainer="your-email@example.com"
LABEL description="Concurrent Web Crawler with ML-integrated indexing"
LABEL version="1.0.0"

# Create non-root user for security
RUN addgroup -g 1000 crawler && \
    adduser -u 1000 -G crawler -D -h /app crawler

# Install required packages
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Create data directory for SQLite
RUN mkdir -p /app/data /app/logs && \
    chown -R crawler:crawler /app

# Copy JAR from build stage
COPY --from=build /build/target/concurrent-web-crawler-*.jar crawler.jar

# Switch to non-root user
USER crawler

# Expose health check port
EXPOSE 8080

# Set default environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV DB_PATH="/app/data/crawler.db"
ENV LOG_LEVEL="INFO"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar crawler.jar $@"]

# Default command (can be overridden)
CMD ["--help"]
