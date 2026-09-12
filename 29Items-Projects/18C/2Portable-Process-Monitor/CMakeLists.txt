cmake_minimum_required(VERSION 3.20)

project(ppmon
    VERSION 0.1.0
    DESCRIPTION "Portable Process Monitor — lightweight Windows process telemetry"
    LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(CompilerWarnings)

# Statically link the CRT on MSVC so the binary is genuinely portable
# (no VC++ redistributable required). Honour per-config selection.
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

# --- Core library (everything except main, so tests can link it) ---
add_library(ppmon_core STATIC
    src/status.c
    src/config.c
    src/log.c
    src/timer.c
    src/process_enum.c
    src/metrics.c
    src/pdh_counters.c
    src/sample_store.c
    src/alerting.c
    src/csv_export.c
    src/console_ui.c
    src/net_server.c
)
target_include_directories(ppmon_core PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
ppmon_set_warnings(ppmon_core)

# Windows system import libraries used by the collection / network layers.
# ntdll is NOT linked: NtQuerySystemInformation is resolved at runtime via
# GetProcAddress, keeping the static import surface to documented APIs only.
if(WIN32)
    target_link_libraries(ppmon_core PUBLIC psapi pdh ws2_32)
    target_compile_definitions(ppmon_core PUBLIC WIN32_LEAN_AND_MEAN NOMINMAX)
endif()

# --- Executable ---
add_executable(ppmon src/main.c)
target_link_libraries(ppmon PRIVATE ppmon_core)
ppmon_set_warnings(ppmon)

# --- Tests ---
include(CTest)
if(BUILD_TESTING)
    add_subdirectory(tests)
endif()

# --- Packaging (portable ZIP) ---
include(Packaging)

install(TARGETS ppmon RUNTIME DESTINATION .)
install(FILES config/ppmon.ini.example README.md DESTINATION .)
