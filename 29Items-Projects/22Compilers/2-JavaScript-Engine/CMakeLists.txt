cmake_minimum_required(VERSION 3.22)

project(JavaScriptEngine VERSION 0.1.0 LANGUAGES CXX)

include(cmake/ToolchainOptions.cmake)

option(JSENGINE_BUILD_TESTS "Build unit and integration tests" ON)
option(JSENGINE_ENABLE_JIT "Enable trace JIT support" ON)
option(JSENGINE_ENABLE_DEBUG_PROTOCOL "Enable debug protocol support" ON)
option(JSENGINE_ENABLE_COVERAGE "Enable GCC/Clang coverage instrumentation" OFF)

find_package(SQLite3 REQUIRED)

add_library(jsengine
    src/engine.cpp
    src/compiler/lexer.cpp
    src/compiler/bytecode_compiler.cpp
    src/compiler/parser.cpp
    src/debug/debug_session_api.cpp
    src/runtime/garbage_collector.cpp
    src/runtime/object.cpp
    src/runtime/value.cpp
    src/storage/metadata_store.cpp
    src/vm/bytecode.cpp
    src/vm/inline_cache.cpp
    src/vm/jit_trace.cpp
    src/vm/virtual_machine.cpp
)

add_library(jsengine::jsengine ALIAS jsengine)

target_include_directories(jsengine
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

target_compile_features(jsengine PUBLIC cxx_std_20)
target_link_libraries(jsengine PUBLIC SQLite::SQLite3)
jsengine_apply_default_options(jsengine)

if(JSENGINE_ENABLE_JIT)
    target_compile_definitions(jsengine PUBLIC JSENGINE_ENABLE_JIT=1)
endif()

if(JSENGINE_ENABLE_DEBUG_PROTOCOL)
    target_compile_definitions(jsengine PUBLIC JSENGINE_ENABLE_DEBUG_PROTOCOL=1)
endif()

add_executable(jsengine_cli
    src/main.cpp
    src/tools/jsengine_cli.cpp
)

target_link_libraries(jsengine_cli PRIVATE jsengine::jsengine)
jsengine_apply_default_options(jsengine_cli)

if(JSENGINE_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
