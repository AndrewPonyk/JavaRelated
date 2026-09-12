$ErrorActionPreference = "Stop"

cmake --preset debug
cmake --build --preset debug
ctest --test-dir build/debug --output-on-failure
