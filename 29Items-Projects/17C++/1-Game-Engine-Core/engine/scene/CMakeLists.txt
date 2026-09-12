# engine/scene — ECS-backed world + serialization (the save/load unit).

add_library(engine_scene src/Scene.cpp)
add_library(engine::scene ALIAS engine_scene)

target_include_directories(engine_scene
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(engine_scene
    PUBLIC  engine::core engine::resources
    PRIVATE engine_project_options engine::platform
)

if(TARGET nlohmann_json::nlohmann_json)
    target_link_libraries(engine_scene PRIVATE nlohmann_json::nlohmann_json)
    target_compile_definitions(engine_scene PRIVATE ENGINE_HAS_JSON=1)
endif()
