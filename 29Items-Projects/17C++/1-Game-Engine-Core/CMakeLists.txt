cmake_minimum_required(VERSION 3.24)

project(GameEngineCore
    VERSION 0.1.0
    DESCRIPTION "Data-oriented C++20 game engine: ECS core, Vulkan rendering, ML procedural terrain"
    LANGUAGES CXX
)

# ----------------------------------------------------------------------------
# Global settings
# ----------------------------------------------------------------------------
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Generate compile_commands.json for clang-tidy / IDEs.
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Put all binaries in a predictable place.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# Default build type for single-config generators.
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE "RelWithDebInfo" CACHE STRING "" FORCE)
endif()

list(APPEND CMAKE_MODULE_PATH
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake"
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/modules")

# ----------------------------------------------------------------------------
# Project options
# ----------------------------------------------------------------------------
option(ENGINE_BUILD_TESTS       "Build the GoogleTest test suite"      ON)
option(ENGINE_BUILD_BENCHMARKS  "Build micro-benchmarks"               OFF)
option(ENGINE_BUILD_EDITOR      "Build the ImGui editor target"        ON)
option(ENGINE_ENABLE_VULKAN     "Enable the Vulkan render backend"     ON)
option(ENGINE_ENABLE_OPENGL     "Enable the OpenGL render backend"     ON)
option(ENGINE_ENABLE_SANITIZERS "Enable ASan/UBSan (debug builds)"     OFF)
option(ENGINE_WARNINGS_AS_ERRORS "Treat compiler warnings as errors"   ON)

# ----------------------------------------------------------------------------
# Shared compiler config + dependencies + static analysis
# ----------------------------------------------------------------------------
include(CompilerWarnings)
include(Dependencies)
include(StaticAnalysis)

# An INTERFACE target every engine module links to pick up warnings/standard.
add_library(engine_project_options INTERFACE)
target_compile_features(engine_project_options INTERFACE cxx_std_20)
engine_set_project_warnings(engine_project_options)
engine_enable_sanitizers(engine_project_options)

# Cross-platform code uses standard C functions (getenv, fopen) that MSVC flags as
# "unsafe". Suppress globally rather than littering Windows-only _s variants.
if(MSVC)
    target_compile_definitions(engine_project_options INTERFACE
        _CRT_SECURE_NO_WARNINGS NOMINMAX WIN32_LEAN_AND_MEAN)
endif()

# ----------------------------------------------------------------------------
# Engine modules (order matters: dependencies first)
# ----------------------------------------------------------------------------
add_subdirectory(engine/core)
add_subdirectory(engine/platform)
add_subdirectory(engine/rendering)
add_subdirectory(engine/resources)
add_subdirectory(engine/physics)
add_subdirectory(engine/audio)
add_subdirectory(engine/scripting)
add_subdirectory(engine/procgen)
add_subdirectory(engine/scene)

# Composition roots
add_subdirectory(runtime)
if(ENGINE_BUILD_EDITOR)
    add_subdirectory(editor)
endif()

# Samples (Vulkan offscreen demo) — self-skips unless the Vulkan SDK is present.
add_subdirectory(samples)

# ----------------------------------------------------------------------------
# Tests & benchmarks
# ----------------------------------------------------------------------------
if(ENGINE_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

if(ENGINE_BUILD_BENCHMARKS)
    add_subdirectory(benchmarks)
endif()

# ----------------------------------------------------------------------------
# Summary
# ----------------------------------------------------------------------------
message(STATUS "================ GameEngineCore ${PROJECT_VERSION} ================")
message(STATUS "  Build type        : ${CMAKE_BUILD_TYPE}")
message(STATUS "  Vulkan backend    : ${ENGINE_ENABLE_VULKAN}")
message(STATUS "  OpenGL backend    : ${ENGINE_ENABLE_OPENGL}")
message(STATUS "  Tests             : ${ENGINE_BUILD_TESTS}")
message(STATUS "  Editor            : ${ENGINE_BUILD_EDITOR}")
message(STATUS "  Sanitizers        : ${ENGINE_ENABLE_SANITIZERS}")
message(STATUS "==============================================================")
