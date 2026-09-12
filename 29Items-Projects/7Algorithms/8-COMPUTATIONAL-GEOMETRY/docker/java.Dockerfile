FROM maven:3.9-eclipse-temurin-11

WORKDIR /workspace

COPY src/java ./src/java
COPY data ./data

WORKDIR /workspace/src/java

CMD ["sh", "-c", "mvn test && java -cp target/classes org.computationalgeometry.cli.Main --input ../../data/fixtures/default_points.csv"]
