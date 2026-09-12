cmake_minimum_required(VERSION 3.20)
project(static_analyzer_libtooling LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(LLVM REQUIRED CONFIG)
find_package(Clang REQUIRED CONFIG)

add_executable(static-analyzer
  src/main.cpp
  src/ast_extractor.cpp
)

target_include_directories(static-analyzer
  PRIVATE
    include
    ${LLVM_INCLUDE_DIRS}
    ${CLANG_INCLUDE_DIRS}
)

target_link_libraries(static-analyzer
  PRIVATE
    clangTooling
    clangBasic
    clangASTMatchers
)

enable_testing()
add_test(NAME static_analyzer_smoke COMMAND static-analyzer --help)
