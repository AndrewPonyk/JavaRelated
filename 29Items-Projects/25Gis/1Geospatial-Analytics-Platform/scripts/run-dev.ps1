param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"

if ($Build) {
    docker compose up --build
} else {
    docker compose up
}
