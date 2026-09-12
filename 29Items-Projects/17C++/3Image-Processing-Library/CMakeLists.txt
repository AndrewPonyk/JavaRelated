cmake_minimum_required(VERSION 3.24)

project(imgproc
    VERSION 0.1.0
    DESCRIPTION "CUDA-accelerated computer-vision library (detection & tracking)"
    LANGUAGES C CXX)

# ----------------------------------------------------------------------------
# Options. The DEFAULT build is dependency-light and self-contained: pure C++20
# core + vendored SQLite + a Netpbm codec, so it compiles and tests anywhere.
# OpenCV / CUDA / ONNX / pybind11 are OPTIONAL accelerated backends.
# ----------------------------------------------------------------------------
option(IMGPROC_WITH_CUDA     "Build CUDA-accelerated paths"             OFF)
option(IMGPROC_WITH_OPENCV   "Build the optional OpenCV codec/backends" OFF)
option(IMGPROC_WITH_ONNX     "Build the ONNX Runtime inference backend" OFF)
option(IMGPROC_BUILD_PYTHON  "Build the pybind11 Python bindings"       OFF)
option(IMGPROC_BUILD_TESTS   "Build GoogleTest unit/integration tests"  ON)
option(IMGPROC_BUILD_BENCH   "Build google/benchmark micro-benchmarks"  OFF)
option(IMGPROC_BUILD_CLI     "Build the reference CLI application"       ON)
option(IMGPROC_WARNINGS_AS_ERRORS "Treat compiler warnings as errors"   OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

# Optional CUDA language — enabled only if requested and a compiler is found.
if(IMGPROC_WITH_CUDA)
    include(CheckLanguage)
    check_language(CUDA)
    if(CMAKE_CUDA_COMPILER)
        enable_language(CUDA)
        message(STATUS "CUDA enabled: ${CMAKE_CUDA_COMPILER}")
    else()
        message(WARNING "CUDA requested but no CUDA compiler found; disabling.")
        set(IMGPROC_WITH_CUDA OFF)
    endif()
endif()

include(CompilerWarnings)
include(CudaOptions)
include(Dependencies)

if(IMGPROC_WITH_ONNX)
    include(Onnxruntime)
endif()

# Generate version.hpp from the template.
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/include/imgproc/version.hpp.in"
    "${CMAKE_CURRENT_BINARY_DIR}/generated/imgproc/version.hpp"
    @ONLY)

# ----------------------------------------------------------------------------
# Vendored third-party (SQLite amalgamation).
# ----------------------------------------------------------------------------
add_subdirectory(third_party/sqlite)

# ----------------------------------------------------------------------------
# Targets
# ----------------------------------------------------------------------------
add_subdirectory(src)

if(IMGPROC_BUILD_CLI)
    add_subdirectory(apps/cli)
endif()

if(IMGPROC_BUILD_PYTHON)
    add_subdirectory(bindings/python)
endif()

if(IMGPROC_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

if(IMGPROC_BUILD_BENCH)
    add_subdirectory(benchmarks)
endif()

message(STATUS "imgproc configuration:")
message(STATUS "  CUDA   : ${IMGPROC_WITH_CUDA}")
message(STATUS "  OpenCV : ${IMGPROC_WITH_OPENCV}")
message(STATUS "  ONNX   : ${IMGPROC_WITH_ONNX}")
message(STATUS "  Python : ${IMGPROC_BUILD_PYTHON}")
message(STATUS "  Tests  : ${IMGPROC_BUILD_TESTS}")
message(STATUS "  CLI    : ${IMGPROC_BUILD_CLI}")
