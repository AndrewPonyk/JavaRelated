# engine/core — foundation library (ECS, memory, jobs, math, events, logging).
# Depends on nothing else in the engine; everything depends on this.

find_package(Threads REQUIRED)

add_library(engine_core
    src/Log.cpp
    src/Json.cpp
    src/ecs/Registry.cpp
    src/memory/PoolAllocator.cpp
    src/jobs/JobSystem.cpp
)
add_library(engine::core ALIAS engine_core)

target_include_directories(engine_core
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:include>
)

target_link_libraries(engine_core
    PUBLIC
        Threads::Threads
    PRIVATE
        engine_project_options  # warnings/-Werror apply to our sources, not consumers'
)

# Optional integrations (linked only if resolved by vcpkg).
if(TARGET glm::glm)
    target_link_libraries(engine_core PUBLIC glm::glm)
    target_compile_definitions(engine_core PUBLIC ENGINE_HAS_GLM=1)
endif()
if(TARGET spdlog::spdlog)
    target_link_libraries(engine_core PUBLIC spdlog::spdlog)
    target_compile_definitions(engine_core PUBLIC ENGINE_HAS_SPDLOG=1)
endif()

# Assertions on in Debug; std::format-aware logging where supported.
target_compile_definitions(engine_core PUBLIC
    $<$<CONFIG:Debug>:ENGINE_ENABLE_ASSERTS>
    $<$<CONFIG:Release>:ENGINE_SHIPPING>
)
