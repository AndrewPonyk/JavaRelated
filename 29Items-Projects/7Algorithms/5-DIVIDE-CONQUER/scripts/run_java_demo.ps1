$ErrorActionPreference = "Stop"

mvn -q compile
java -cp target/classes com.divideconquer.app.DemoRunner @args
