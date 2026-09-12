# ============================================================================
#  Real-Time Trading System — build
#
#  The DEFAULT build is fully self-contained (C++20 standard library only): the
#  complete engine core, the in-process FIX simulated exchange, file-based
#  journaling, a local predictor, the trading_engine app, and the test suite.
#
#  Heavy production integrations are OPTIONAL features (real adapters, OFF by
#  default) so the project builds and its tests pass anywhere with just a
#  compiler:
#     -DRTS_ENABLE_BOOST=ON     Boost.Asio TCP gateway/feed
#     -DRTS_ENABLE_GRPC=ON      gRPC client to the LSTM service
#     -DRTS_ENABLE_QT=ON        Qt dashboard
#     -DRTS_ENABLE_ORACLE=ON    Oracle OCCI persistence
#     -DRTS_ENABLE_TBB=ON       Intel TBB analytics
#     -DRTS_BUILD_BENCHMARKS=ON Google Benchmark microbenchmarks
# ============================================================================
cmake_minimum_required(VERSION 3.20)
project(rts_trading_system LANGUAGES CXX VERSION 0.1.0)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
list(APPEND CMAKE_MODULE_PATH "${CMAKE_SOURCE_DIR}/cmake")

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# ---- Options ---------------------------------------------------------------
option(RTS_BUILD_TESTS      "Build the test suite"          ON)
option(RTS_BUILD_BENCHMARKS "Build microbenchmarks"         OFF)
option(RTS_ENABLE_BOOST     "Boost.Asio TCP transport"      OFF)
option(RTS_ENABLE_GRPC      "gRPC ML client"                OFF)
option(RTS_ENABLE_QT        "Qt dashboard"                  OFF)
option(RTS_ENABLE_ORACLE    "Oracle OCCI persistence"       OFF)
option(RTS_ENABLE_TBB       "Intel TBB analytics"           OFF)

# ---- Warnings / platform ---------------------------------------------------
add_library(rts_flags INTERFACE)
if(MSVC)
    # /wd4324: padding due to alignas is intentional (cache-line isolation).
    target_compile_options(rts_flags INTERFACE /W4 /permissive- /Zc:__cplusplus /EHsc /wd4324)
    target_compile_definitions(rts_flags INTERFACE NOMINMAX WIN32_LEAN_AND_MEAN
                               _CRT_SECURE_NO_WARNINGS)
else()
    target_compile_options(rts_flags INTERFACE -Wall -Wextra)
    # The cache-line constant is used for runtime alignment only (not a persisted
    # ABI), so GCC's advisory -Winterference-size does not apply here.
    if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        target_compile_options(rts_flags INTERFACE -Wno-interference-size)
    endif()
    find_package(Threads REQUIRED)
    target_link_libraries(rts_flags INTERFACE Threads::Threads)
endif()

# ---- Core engine library (dependency-free) ---------------------------------
add_library(rts_core STATIC
    src/common/Config.cpp
    src/fix/FixSession.cpp
    src/marketdata/OrderBook.cpp
    src/oms/OrderManager.cpp
    src/risk/RiskEngine.cpp
    src/backtest/BacktestEngine.cpp
    src/ml/PredictionClient.cpp
    src/exchange/SimulatedExchange.cpp
    src/engine/Engine.cpp
    src/persistence/FileTradeRepository.cpp
)
target_include_directories(rts_core PUBLIC src)
target_link_libraries(rts_core PUBLIC rts_flags)

# ---- Optional production integrations --------------------------------------
if(RTS_ENABLE_BOOST)
    find_package(Boost REQUIRED COMPONENTS system)
    target_link_libraries(rts_core PUBLIC Boost::system)
    target_compile_definitions(rts_core PUBLIC RTS_ENABLE_BOOST)
endif()
if(RTS_ENABLE_TBB)
    find_package(TBB REQUIRED)
    target_link_libraries(rts_core PUBLIC TBB::tbb)
    target_compile_definitions(rts_core PUBLIC RTS_ENABLE_TBB)
endif()
if(RTS_ENABLE_GRPC)
    find_package(gRPC CONFIG REQUIRED)
    find_package(Protobuf REQUIRED)
    target_compile_definitions(rts_core PUBLIC RTS_ENABLE_GRPC)
    # Optional (RTS_ENABLE_GRPC): protobuf_generate + grpc_generate for the proto
endif()
if(RTS_ENABLE_ORACLE)
    find_package(Oracle REQUIRED)
    target_link_libraries(rts_core PUBLIC Oracle::OCCI)
    target_compile_definitions(rts_core PUBLIC RTS_ENABLE_ORACLE)
endif()

# ---- Executables -----------------------------------------------------------
add_executable(trading_engine apps/trading_engine/main.cpp)
target_link_libraries(trading_engine PRIVATE rts_core)

add_executable(backtest apps/backtest/main.cpp)
target_link_libraries(backtest PRIVATE rts_core)

# ---- Qt dashboard (optional) -----------------------------------------------
if(RTS_ENABLE_QT)
    find_package(Qt6 COMPONENTS Widgets REQUIRED)
    set(CMAKE_AUTOMOC ON)
    add_executable(dashboard apps/dashboard/main.cpp src/ui/MainWindow.cpp)
    target_link_libraries(dashboard PRIVATE rts_core Qt6::Widgets)
endif()

# ---- Tests -----------------------------------------------------------------
if(RTS_BUILD_TESTS)
    enable_testing()
    find_package(GTest QUIET)
    if(GTest_FOUND)
        message(STATUS "Using system GoogleTest")
        set(RTS_TEST_LIB GTest::gtest_main)
    else()
        message(STATUS "GoogleTest not found — using bundled tests/shim runner")
        add_library(rts_test_shim STATIC tests/shim/gtest_main.cpp)
        target_include_directories(rts_test_shim PUBLIC tests/shim)
        set(RTS_TEST_LIB rts_test_shim)
    endif()

    add_executable(rts_unit_tests
        tests/unit/test_spsc_queue.cpp
        tests/unit/test_order_book.cpp
        tests/unit/test_fix_codec.cpp
        tests/unit/test_config.cpp
        tests/unit/test_risk_engine.cpp
        tests/unit/test_order_manager.cpp
        tests/unit/test_robustness.cpp
        tests/integration/test_order_flow.cpp
        tests/integration/test_engine_sim.cpp
        tests/integration/test_backtest.cpp
    )
    target_link_libraries(rts_unit_tests PRIVATE rts_core ${RTS_TEST_LIB})
    add_test(NAME rts_unit_tests COMMAND rts_unit_tests)
endif()

# ---- Benchmarks (optional) -------------------------------------------------
if(RTS_BUILD_BENCHMARKS)
    find_package(benchmark REQUIRED)
    add_executable(rts_benchmarks tests/benchmark/bench_lockfree.cpp)
    target_link_libraries(rts_benchmarks PRIVATE rts_core benchmark::benchmark)
endif()
