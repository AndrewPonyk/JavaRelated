# tests — GoogleTest suites, registered with CTest.
#
# Uses real GoogleTest when available (CI/vcpkg); otherwise falls back to the
# built-in gtest-compatible shim in tests/framework so the suite runs dependency-free.

set(ENGINE_TEST_SOURCES
    core/test_registry.cpp
    core/test_sparse_set.cpp
    core/test_pool_allocator.cpp
    core/test_allocators.cpp
    core/test_json.cpp
    core/test_command_buffer.cpp
    core/test_jobs.cpp
    core/test_event_bus.cpp
    core/test_math.cpp
    physics/test_physics.cpp
    audio/test_audio.cpp
    scene/test_scene.cpp
    resources/test_asset_database.cpp
    resources/test_asset_manager.cpp
    resources/test_serialization.cpp
    scripting/test_script_engine.cpp
    rendering/test_render_graph.cpp
    procgen/test_terrain_generator.cpp
)

add_executable(engine_tests ${ENGINE_TEST_SOURCES})

# Tests intentionally ignore some [[nodiscard]] results (e.g., initialize() in a
# context where failure is impossible). Silence just that warning for the suite.
if(MSVC)
    target_compile_options(engine_tests PRIVATE /wd4834)
else()
    target_compile_options(engine_tests PRIVATE -Wno-unused-result)
endif()

# Absolute path to shipped assets, so integration tests can load real asset files
# regardless of the test working directory.
target_compile_definitions(engine_tests PRIVATE
    ENGINE_ASSETS_DIR="${CMAKE_SOURCE_DIR}/assets")

target_link_libraries(engine_tests
    PRIVATE
        engine::core
        engine::platform
        engine::rendering
        engine::resources
        engine::physics
        engine::audio
        engine::scene
        engine::scripting
        engine::procgen
)

if(TARGET GTest::gtest_main)
    message(STATUS "[tests] Using system GoogleTest.")
    target_link_libraries(engine_tests PRIVATE GTest::gtest_main)
    include(GoogleTest)
    gtest_discover_tests(engine_tests PROPERTIES LABELS "unit" DISCOVERY_TIMEOUT 30)
else()
    message(STATUS "[tests] GoogleTest not found — using the built-in shim.")
    target_sources(engine_tests PRIVATE framework/gtest_main.cpp)
    target_include_directories(engine_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/framework)
    add_test(NAME engine_tests COMMAND engine_tests)
    set_tests_properties(engine_tests PROPERTIES LABELS "unit")
endif()
