# engine/resources — asset manager, serialization, asset/procgen database.

add_library(engine_resources
    src/AssetManager.cpp
    src/AssetDatabase.cpp
    src/Serialization.cpp
)
add_library(engine::resources ALIAS engine_resources)

target_include_directories(engine_resources
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)

target_link_libraries(engine_resources
    PUBLIC  engine::core
    PRIVATE engine_project_options engine::platform
)

# JSON: the engine ships a built-in parser (engine/core/Json). nlohmann is an
# optional drop-in for hot paths.
if(TARGET nlohmann_json::nlohmann_json)
    target_link_libraries(engine_resources PRIVATE nlohmann_json::nlohmann_json)
    target_compile_definitions(engine_resources PRIVATE ENGINE_HAS_JSON=1)
endif()

# The AssetDatabase persists to JSON by default (no external dep). SQLite is the
# documented production backend (see /migrations); link it when available.
if(TARGET unofficial::sqlite3::sqlite3)
    target_link_libraries(engine_resources PRIVATE unofficial::sqlite3::sqlite3)
    target_compile_definitions(engine_resources PRIVATE ENGINE_HAS_SQLITE=1)
endif()
