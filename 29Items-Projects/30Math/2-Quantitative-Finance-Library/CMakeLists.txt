# Top-level CMake — invoked by scikit-build-core (pip install) or directly for C++ dev.
cmake_minimum_required(VERSION 3.24)
project(quantfinlib VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(QFCORE_BUILD_TESTS "Build C++ unit tests (Catch2)" ON)
option(QFCORE_BUILD_BINDINGS "Build the pybind11 Python module" ON)

# Reproducibility over speed: keep FP behavior strict and identical across compilers.
if(MSVC)
    add_compile_options(/W4 /fp:precise)
    add_compile_definitions(_USE_MATH_DEFINES)
else()
    add_compile_options(-Wall -Wextra -Wpedantic -ffp-contract=off)
endif()

add_subdirectory(cpp)
