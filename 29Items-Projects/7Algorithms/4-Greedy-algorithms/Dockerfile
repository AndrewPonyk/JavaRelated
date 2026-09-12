FROM maven:3.9-eclipse-temurin-17 AS java-build
WORKDIR /app/java
COPY java/pom.xml .
COPY java/src ./src
RUN mvn -q -DskipTests package

FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml README.md ./
COPY python ./python
COPY data ./data
COPY --from=java-build /app/java/target/greedy-algorithms-1.0.0.jar ./greedy-algorithms.jar
RUN pip install --no-cache-dir -e .
CMD ["python", "-m", "greedy_algorithms"]
