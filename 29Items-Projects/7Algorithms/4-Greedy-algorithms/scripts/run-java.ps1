$ErrorActionPreference = "Stop"
Push-Location "$PSScriptRoot\..\java"
try {
    mvn -q exec:java
}
finally {
    Pop-Location
}
