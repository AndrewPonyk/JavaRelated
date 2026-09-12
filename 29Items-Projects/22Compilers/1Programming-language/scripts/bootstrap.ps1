param(
    [switch]$WithLlvm
)

$ErrorActionPreference = "Stop"

$buildDir = "build"
$llvmFlag = if ($WithLlvm) { "ON" } else { "OFF" }

cmake -S . -B $buildDir -DPLANG_ENABLE_TESTS=ON -DPLANG_ENABLE_LLVM=$llvmFlag
cmake --build $buildDir
ctest --test-dir $buildDir --output-on-failure
