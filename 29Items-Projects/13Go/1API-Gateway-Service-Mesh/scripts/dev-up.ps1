param(
    [switch]$Build
)

$composeArgs = @("up")
if ($Build) {
    $composeArgs += "--build"
}

docker compose @composeArgs
