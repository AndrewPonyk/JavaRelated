# CMakeLists.txt — cross-platform build (MSVC/Windows, gcc/clang on *nix).
#
# The GNU Makefile remains the primary Linux developer path; this CMake build
# exists so the project also configures/builds/tests with MSVC + CMake/CTest and
# in IDEs. Mirrors the Makefile's structure and feature toggles.
#
#   cmake -S . -B build -G Ninja
#   cmake --build build
#   ctest --test-dir build --output-on-failure
#   ./build/cpuemu --selftest --headless
cmake_minimum_required(VERSION 3.16)
project(cpuemu C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

option(CPUEMU_ENABLE_GUI "Build the SDL2 visualizer"     OFF)
option(CPUEMU_USE_ASM_ALU "Use the NASM ALU fast path"   OFF)
option(CPUEMU_WERROR      "Treat warnings as errors"     OFF)

enable_testing()

# Per-target warning flags / defines, applied uniformly (per compiler).
function(cpuemu_setup tgt)
    target_include_directories(${tgt} PRIVATE src tests)
    if(MSVC)
        target_compile_options(${tgt} PRIVATE /W3)
        target_compile_definitions(${tgt} PRIVATE _CRT_SECURE_NO_WARNINGS)
        if(CPUEMU_WERROR)
            target_compile_options(${tgt} PRIVATE /WX)
        endif()
    else()
        target_compile_options(${tgt} PRIVATE
            -Wall -Wextra -Wshadow -Wno-format-truncation)
        if(CPUEMU_WERROR)
            target_compile_options(${tgt} PRIVATE -Werror)
        endif()
    endif()
    if(CPUEMU_ENABLE_GUI)
        target_compile_definitions(${tgt} PRIVATE CPUEMU_ENABLE_GUI=1)
    endif()
    if(CPUEMU_USE_ASM_ALU)
        target_compile_definitions(${tgt} PRIVATE CPUEMU_USE_ASM_ALU=1)
    endif()
endfunction()

# ---- core engine as a static library (pure, no I/O libraries) --------------
file(GLOB LIB_SOURCES CONFIGURE_DEPENDS
     src/common/*.c src/core/*.c src/loader/*.c)
add_library(cpuemu_core STATIC ${LIB_SOURCES})
target_include_directories(cpuemu_core PUBLIC src)
cpuemu_setup(cpuemu_core)

# ---- optional NASM ALU path ------------------------------------------------
if(CPUEMU_USE_ASM_ALU)
    enable_language(ASM_NASM)
    target_sources(cpuemu_core PRIVATE src/asm/alu_fast.asm)
endif()

# ---- optional SDL2 GUI -----------------------------------------------------
if(CPUEMU_ENABLE_GUI)
    find_package(SDL2 REQUIRED)
endif()

# ---- emulator executable (main + visualizer; stub when GUI off) ------------
add_executable(cpuemu src/main.c src/gui/visualizer.c)
target_link_libraries(cpuemu PRIVATE cpuemu_core)
cpuemu_setup(cpuemu)
if(CPUEMU_ENABLE_GUI)
    target_link_libraries(cpuemu PRIVATE SDL2::SDL2)
endif()

# ---- tests (one executable per file, registered with CTest) ----------------
file(GLOB TEST_SOURCES CONFIGURE_DEPENDS tests/unit/*.c tests/integration/*.c)
foreach(test_src ${TEST_SOURCES})
    get_filename_component(test_name ${test_src} NAME_WE)
    add_executable(${test_name} ${test_src})
    target_link_libraries(${test_name} PRIVATE cpuemu_core)
    cpuemu_setup(${test_name})
    add_test(NAME ${test_name} COMMAND ${test_name})
endforeach()

# The built-in self-test, run as a CTest case as well.
add_test(NAME selftest COMMAND cpuemu --selftest --headless)
