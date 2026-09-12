cmake_minimum_required(VERSION 3.24)
project(execution_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)  # for clang-tidy

option(ENABLE_LTO "Enable link-time optimization (Release)" OFF)
option(BUILD_TESTING "Build unit tests" ON)

# Warnings as a quality gate.
add_library(exec_warnings INTERFACE)
target_compile_options(exec_warnings INTERFACE
    $<$<CXX_COMPILER_ID:GNU,Clang>:-Wall -Wextra -Wpedantic -Werror>
)

# --- core library ---------------------------------------------------------
add_library(execution_core
    src/order_router.cpp
    src/fix_session.cpp
)
target_include_directories(execution_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(execution_core PRIVATE exec_warnings)

# On the production exec host, tune for the local CPU (Phase 3 latency work).
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(execution_core PRIVATE -O3 -march=native)
    if(ENABLE_LTO)
        set_target_properties(execution_core PROPERTIES INTERPROCEDURAL_OPTIMIZATION ON)
    endif()
endif()

# TODO(phase-1): find_package(RdKafka) and QuickFIX; link them into execution_core.

# --- executable -----------------------------------------------------------
add_executable(execution_engine src/main.cpp)
target_link_libraries(execution_engine PRIVATE execution_core exec_warnings)

# --- tests ----------------------------------------------------------------
if(BUILD_TESTING)
    enable_testing()
    find_package(GTest QUIET)
    if(NOT GTest_FOUND)
        include(FetchContent)
        FetchContent_Declare(googletest
            URL https://github.com/google/googletest/archive/refs/tags/v1.14.0.zip)
        FetchContent_MakeAvailable(googletest)
    endif()

    add_executable(test_order_router tests/test_order_router.cpp)
    target_link_libraries(test_order_router PRIVATE execution_core GTest::gtest_main)

    include(GoogleTest)
    gtest_discover_tests(test_order_router)
endif()
