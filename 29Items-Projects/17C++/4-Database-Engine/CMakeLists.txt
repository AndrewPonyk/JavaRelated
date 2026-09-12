cmake_minimum_required(VERSION 3.20)
project(minidb
  VERSION 0.1.0
  DESCRIPTION "Educational B+ Tree / mmap / LRU database engine"
  LANGUAGES CXX)

# ---------------------------------------------------------------------------
# Options
# ---------------------------------------------------------------------------
option(MINIDB_BUILD_TESTS "Build the unit/integration tests" ON)
option(MINIDB_BUILD_CLI   "Build the interactive CLI shell"  ON)
option(MINIDB_WARNINGS_AS_ERRORS "Treat compiler warnings as errors" OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Default to Release for single-config generators if nothing was specified.
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
  set_property(CACHE CMAKE_BUILD_TYPE PROPERTY STRINGS
    Debug Release RelWithDebInfo MinSizeRel)
endif()

list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
include(CompilerWarnings)

# ---------------------------------------------------------------------------
# Core library
# ---------------------------------------------------------------------------
add_library(minidb_core
  src/common/status.cpp
  src/common/types.cpp
  src/storage/mmap_file.cpp
  src/storage/disk_manager.cpp
  src/storage/lru_replacer.cpp
  src/storage/buffer_pool_manager.cpp
  src/storage/table_heap.cpp
  src/index/bplus_tree.cpp
  src/catalog/catalog.cpp
  src/catalog/tuple_codec.cpp
  src/parser/lexer.cpp
  src/parser/parser.cpp
  src/execution/cost_estimator.cpp
  src/execution/executor.cpp
  src/engine/database.cpp
)
add_library(minidb::core ALIAS minidb_core)

target_include_directories(minidb_core
  PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_features(minidb_core PUBLIC cxx_std_20)
minidb_set_warnings(minidb_core)

find_package(Threads REQUIRED)
target_link_libraries(minidb_core PUBLIC Threads::Threads)

# ---------------------------------------------------------------------------
# CLI shell
# ---------------------------------------------------------------------------
if(MINIDB_BUILD_CLI)
  add_executable(minidb src/cli/main.cpp)
  target_link_libraries(minidb PRIVATE minidb_core)
  minidb_set_warnings(minidb)
endif()

# ---------------------------------------------------------------------------
# Tests (GoogleTest via FetchContent)
# ---------------------------------------------------------------------------
if(MINIDB_BUILD_TESTS)
  enable_testing()
  include(FetchContent)
  FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG        v1.14.0
  )
  # Keep MSVC runtime consistent with this project.
  set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
  FetchContent_MakeAvailable(googletest)
  include(GoogleTest)
  add_subdirectory(tests)
endif()

message(STATUS "MiniDB ${PROJECT_VERSION} | tests=${MINIDB_BUILD_TESTS} "
               "cli=${MINIDB_BUILD_CLI} build=${CMAKE_BUILD_TYPE}")
