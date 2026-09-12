# StaticAnalysis.cmake
# Opt-in clang-tidy / cppcheck integration. Off by default so normal builds stay fast;
# CI turns these on. Honors the repo .clang-tidy.

include_guard(GLOBAL)

option(ENGINE_ENABLE_CLANG_TIDY "Run clang-tidy during the build" OFF)
option(ENGINE_ENABLE_CPPCHECK   "Run cppcheck during the build"   OFF)

if(ENGINE_ENABLE_CLANG_TIDY)
    find_program(CLANG_TIDY_EXE NAMES clang-tidy)
    if(CLANG_TIDY_EXE)
        set(CMAKE_CXX_CLANG_TIDY
            "${CLANG_TIDY_EXE};--extra-arg=-Wno-unknown-warning-option"
            CACHE STRING "" FORCE)
        message(STATUS "clang-tidy enabled: ${CLANG_TIDY_EXE}")
    else()
        message(WARNING "ENGINE_ENABLE_CLANG_TIDY=ON but clang-tidy was not found.")
    endif()
endif()

if(ENGINE_ENABLE_CPPCHECK)
    find_program(CPPCHECK_EXE NAMES cppcheck)
    if(CPPCHECK_EXE)
        set(CMAKE_CXX_CPPCHECK
            "${CPPCHECK_EXE};--enable=warning,performance,portability;--inline-suppr;--std=c++20"
            CACHE STRING "" FORCE)
        message(STATUS "cppcheck enabled: ${CPPCHECK_EXE}")
    else()
        message(WARNING "ENGINE_ENABLE_CPPCHECK=ON but cppcheck was not found.")
    endif()
endif()
