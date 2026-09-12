# engine/platform — windowing (SDL2), input, filesystem, timing.

add_library(engine_platform
    src/Window.cpp
    src/Input.cpp
    src/Filesystem.cpp
)
add_library(engine::platform ALIAS engine_platform)

target_include_directories(engine_platform
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)

target_link_libraries(engine_platform
    PUBLIC engine::core
    PRIVATE engine_project_options
)

# SDL2 is optional at configure time so the tree builds headless in CI.
if(TARGET SDL2::SDL2)
    target_link_libraries(engine_platform PUBLIC SDL2::SDL2)
    target_compile_definitions(engine_platform PRIVATE ENGINE_HAS_SDL2=1)
else()
    message(WARNING "[platform] SDL2 not found — windowing builds in headless mode.")
endif()
