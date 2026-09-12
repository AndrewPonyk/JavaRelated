cmake_minimum_required(VERSION 3.24)

project(ProgrammingLanguage
    VERSION 0.1.0
    DESCRIPTION "DSL compiler with Pratt parsing, LLVM IR generation, JIT, and package management"
    LANGUAGES CXX)

option(PLANG_ENABLE_TESTS "Build test executables" ON)
option(PLANG_ENABLE_LLVM "Enable LLVM/MLIR integration when dependencies are available" OFF)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(CompilerWarnings)

add_library(plang_core
    src/api/PackageApi.cpp
    src/backend/LLVMCodeGen.cpp
    src/frontend/Lexer.cpp
    src/frontend/Parser.cpp
    src/frontend/SourcePreview.cpp
    src/frontend/TypeInferencer.cpp
    src/pkg/PackageManager.cpp
    src/runtime/JitEngine.cpp)

target_include_directories(plang_core
    PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/include")

plang_set_compiler_warnings(plang_core)

if(PLANG_ENABLE_LLVM)
    find_package(LLVM REQUIRED CONFIG)
    message(STATUS "Found LLVM ${LLVM_PACKAGE_VERSION}")
    target_compile_definitions(plang_core PUBLIC PLANG_ENABLE_LLVM=1)
    target_include_directories(plang_core SYSTEM PUBLIC ${LLVM_INCLUDE_DIRS})
    llvm_map_components_to_libnames(PLANG_LLVM_LIBS core executionengine mcjit native orcjit support)
    target_link_libraries(plang_core PUBLIC ${PLANG_LLVM_LIBS})
endif()

add_executable(plang src/main.cpp)
target_link_libraries(plang PRIVATE plang_core)
plang_set_compiler_warnings(plang)

if(PLANG_ENABLE_TESTS)
    enable_testing()

    add_executable(parser_smoke_test tests/unit/parser_smoke_test.cpp)
    target_link_libraries(parser_smoke_test PRIVATE plang_core)
    add_test(NAME parser_smoke_test COMMAND parser_smoke_test)

    add_executable(frontend_error_test tests/unit/frontend_error_test.cpp)
    target_link_libraries(frontend_error_test PRIVATE plang_core)
    add_test(NAME frontend_error_test COMMAND frontend_error_test)

    add_executable(package_api_test tests/unit/package_api_test.cpp)
    target_link_libraries(package_api_test PRIVATE plang_core)
    add_test(NAME package_api_test COMMAND package_api_test)

    add_executable(repl_smoke_test tests/integration/repl_smoke_test.cpp)
    target_link_libraries(repl_smoke_test PRIVATE plang_core)
    add_test(NAME repl_smoke_test COMMAND repl_smoke_test)
endif()
