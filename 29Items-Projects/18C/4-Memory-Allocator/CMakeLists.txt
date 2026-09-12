# ============================================================================
#  Memory Allocator — cross-platform build (Windows/MSVC, Linux/GCC-Clang)
#
#  Quick start (any platform with CMake + a C compiler):
#     cmake -S . -B build
#     cmake --build build
#     ctest --test-dir build --output-on-failure
#
#  Windows one-liner that auto-loads the MSVC environment:
#     scripts\build-windows.bat
# ============================================================================
cmake_minimum_required(VERSION 3.16)
project(memalloc LANGUAGES C VERSION 1.0.0
        DESCRIPTION "Educational memory allocator: first-fit, best-fit, buddy")

# ---- options --------------------------------------------------------------
option(MEMALLOC_DEBUG        "Enable runtime debug checks (leak/double-free)" OFF)
option(MEMALLOC_ASAN         "Build with AddressSanitizer (+UBSan on GCC/Clang)" OFF)
option(MEMALLOC_BUILD_TESTS  "Build unit tests"  ON)
option(MEMALLOC_BUILD_BENCH  "Build benchmarks"  ON)

# ---- language standard ----------------------------------------------------
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

# Default to a Debug build when the user did not pick one (single-config gens).
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Debug CACHE STRING "Build type" FORCE)
endif()

# Put all executables in build/bin for predictable paths.
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/bin)

# ---- the allocator library ------------------------------------------------
file(GLOB_RECURSE MEMALLOC_SOURCES CONFIGURE_DEPENDS src/*.c)
add_library(memalloc STATIC ${MEMALLOC_SOURCES})
# `include/` is the public API. `src/` is exposed too because the in-repo demo,
# benchmarks, and tests intentionally reach into internal headers (stats, debug,
# strategies) for instrumentation — matching the Makefile's `-Iinclude -Isrc`.
target_include_directories(memalloc
  PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include
         ${CMAKE_CURRENT_SOURCE_DIR}/src)

# Warnings: keep MSVC at /W3 (clean) and GCC/Clang strict.
if(MSVC)
  target_compile_options(memalloc PRIVATE /W3)
  # Silence CRT "unsafe function" warnings for the whole target set.
  target_compile_definitions(memalloc PUBLIC _CRT_SECURE_NO_WARNINGS)
else()
  target_compile_options(memalloc PRIVATE
    -Wall -Wextra -Wshadow -Wpointer-arith -Wwrite-strings)
endif()

# MEM_DEBUG must be uniform across the library and everything that links it
# (it changes the block header layout), so expose it PUBLIC.
if(MEMALLOC_DEBUG)
  target_compile_definitions(memalloc PUBLIC MEM_DEBUG)
endif()

# AddressSanitizer (MSVC 16.9+ and GCC/Clang). The substitute for Valgrind on
# Windows. On GCC/Clang we also enable UBSan.
if(MEMALLOC_ASAN)
  if(MSVC)
    # MSVC Debug injects /RTC1, which is incompatible with /fsanitize=address.
    string(REGEX REPLACE "/RTC[1csu]+" "" CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG}")
    target_compile_options(memalloc PUBLIC /fsanitize=address)
  else()
    target_compile_options(memalloc PUBLIC
      -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(memalloc PUBLIC -fsanitize=address,undefined)
  endif()
endif()

# ---- demo (the CLI "frontend") --------------------------------------------
add_executable(demo examples/demo.c)
target_link_libraries(demo PRIVATE memalloc)

# ---- benchmark ------------------------------------------------------------
if(MEMALLOC_BUILD_BENCH)
  add_executable(bench_strategies benchmarks/bench_strategies.c)
  target_link_libraries(bench_strategies PRIVATE memalloc)
endif()

# ---- tests (one executable per tests/test_*.c, registered with CTest) ------
if(MEMALLOC_BUILD_TESTS)
  enable_testing()
  file(GLOB TEST_SOURCES CONFIGURE_DEPENDS tests/test_*.c)
  foreach(test_src ${TEST_SOURCES})
    get_filename_component(test_name ${test_src} NAME_WE)
    add_executable(${test_name} ${test_src})
    target_link_libraries(${test_name} PRIVATE memalloc)
    target_include_directories(${test_name} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/tests)
    add_test(NAME ${test_name} COMMAND ${test_name})
  endforeach()
endif()

message(STATUS "memalloc: build=${CMAKE_BUILD_TYPE} MEM_DEBUG=${MEMALLOC_DEBUG} ASAN=${MEMALLOC_ASAN}")
