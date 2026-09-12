# Network Packet Analyzer — CMake build (primary build on Windows/MSVC).
#
# Windows (offline, zero external deps):
#   cmake -S . -B build -G "NMake Makefiles"
#   cmake --build build
#   ctest --test-dir build --output-on-failure
#   build\npa.exe -r tests\fixtures\sample.pcap --headless --json
#
# Windows + live capture: install Npcap + the Npcap SDK, then set NPCAP_SDK to
# the SDK root (containing Include\ and Lib\x64\) before configuring:
#   set NPCAP_SDK=C:\npcap-sdk
#   cmake -S . -B build -G "NMake Makefiles" && cmake --build build
#
# Linux/macOS: cmake auto-detects libpcap (live) + ncursesw (TUI).
cmake_minimum_required(VERSION 3.15)
project(npa C)

set(NPA_CORE
    src/capture/capture.c
    src/capture/pcap_file.c
    src/buffer/ring_buffer.c
    src/decode/decode.c src/decode/ethernet.c src/decode/ipv4.c src/decode/ipv6.c
    src/decode/tcp.c src/decode/udp.c src/decode/icmp.c
    src/analysis/analyzer.c src/analysis/anomaly.c src/analysis/patterns.c
    src/analysis/statistics.c
    src/ui/tui.c src/ui/views.c
    src/util/config.c src/util/log.c)

# Apply the standard include paths, language level, and platform shims/links.
function(npa_platformize tgt)
    target_include_directories(${tgt} PRIVATE src include)
    if(MSVC)
        target_include_directories(${tgt} PRIVATE src/platform/win)
        target_sources(${tgt} PRIVATE
            src/platform/win/getopt.c src/platform/win/win_compat.c)
        target_compile_options(${tgt} PRIVATE
            /std:c17 /experimental:c11atomics /FInpa_force.h /W3)
        target_compile_definitions(${tgt} PRIVATE _CRT_SECURE_NO_WARNINGS)
    else()
        find_package(Threads REQUIRED)
        target_link_libraries(${tgt} PRIVATE Threads::Threads)
        target_compile_options(${tgt} PRIVATE -std=c11 -Wall -Wextra)
    endif()
endfunction()

# ---- application ----
add_executable(npa src/main.c ${NPA_CORE})
npa_platformize(npa)

if(MSVC)
    if(DEFINED ENV{NPCAP_SDK})
        target_include_directories(npa PRIVATE "$ENV{NPCAP_SDK}/Include")
        target_link_directories(npa PRIVATE "$ENV{NPCAP_SDK}/Lib/x64")
        target_link_libraries(npa PRIVATE wpcap Packet ws2_32)
        target_compile_definitions(npa PRIVATE NPA_HAVE_PCAP=1)
        message(STATUS "npa: live capture ENABLED via Npcap SDK ($ENV{NPCAP_SDK})")
    else()
        message(STATUS "npa: offline-only build — set NPCAP_SDK for live capture")
    endif()
else()
    find_library(PCAP_LIB pcap)
    if(PCAP_LIB)
        target_link_libraries(npa PRIVATE ${PCAP_LIB})
        target_compile_definitions(npa PRIVATE NPA_HAVE_PCAP=1)
    endif()
    find_library(NCURSES_LIB NAMES ncursesw ncurses)
    if(NCURSES_LIB)
        target_link_libraries(npa PRIVATE ${NCURSES_LIB})
        target_compile_definitions(npa PRIVATE NPA_WITH_NCURSES=1)
    endif()
endif()

# ---- test-fixture generator ----
add_executable(gen_sample tests/fixtures/gen_sample.c)
if(MSVC)
    target_compile_definitions(gen_sample PRIVATE _CRT_SECURE_NO_WARNINGS)
endif()

# ---- unit tests (CTest) ----
enable_testing()
function(npa_test name)
    add_executable(${name} ${ARGN})
    npa_platformize(${name})
    target_include_directories(${name} PRIVATE tests)
    add_test(NAME ${name} COMMAND ${name})
endfunction()

npa_test(test_ring_buffer tests/test_ring_buffer.c src/buffer/ring_buffer.c)
npa_test(test_decode      tests/test_decode.c
         src/decode/decode.c src/decode/ethernet.c src/decode/ipv4.c
         src/decode/ipv6.c src/decode/tcp.c src/decode/udp.c src/decode/icmp.c)
npa_test(test_anomaly     tests/test_anomaly.c
         src/analysis/anomaly.c src/analysis/patterns.c src/util/log.c)
npa_test(test_patterns    tests/test_patterns.c src/analysis/patterns.c src/util/log.c)
npa_test(test_config      tests/test_config.c src/util/config.c src/util/log.c)
npa_test(test_statistics  tests/test_statistics.c src/analysis/statistics.c)

# Generate the sample pcap, then a headless smoke test over it.
add_test(NAME fixtures COMMAND gen_sample
         ${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures/sample.pcap)
add_test(NAME smoke COMMAND npa -r
         ${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures/sample.pcap --headless --json)
set_tests_properties(smoke PROPERTIES DEPENDS fixtures)
