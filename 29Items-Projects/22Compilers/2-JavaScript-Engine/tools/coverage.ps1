param(
    [int]$MinimumLineCoverage = 80
)

$ErrorActionPreference = "Stop"

cmake --preset coverage
cmake --build --preset coverage
ctest --test-dir build/coverage --output-on-failure
gcovr --root . --filter "src" --filter "include" --exclude "tests" --fail-under-line $MinimumLineCoverage --print-summary
