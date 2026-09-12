# engine/physics — rigid-body simulation as an ECS system.

add_library(engine_physics src/PhysicsWorld.cpp)
add_library(engine::physics ALIAS engine_physics)

target_include_directories(engine_physics
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(engine_physics
    PUBLIC  engine::core
    PRIVATE engine_project_options
)
# TODO: link Jolt/Bullet when integrating a production physics backend.
