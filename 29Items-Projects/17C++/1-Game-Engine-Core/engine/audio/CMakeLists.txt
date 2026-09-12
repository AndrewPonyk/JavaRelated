# engine/audio — streaming mixer + 3D spatial audio.

add_library(engine_audio src/AudioEngine.cpp)
add_library(engine::audio ALIAS engine_audio)

target_include_directories(engine_audio
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(engine_audio
    PUBLIC  engine::core
    PRIVATE engine_project_options engine::platform
)
# Audio device backend (SDL2 audio subsystem) linked via platform/SDL2 when wired up.
if(TARGET SDL2::SDL2)
    target_link_libraries(engine_audio PRIVATE SDL2::SDL2)
    target_compile_definitions(engine_audio PRIVATE ENGINE_HAS_SDL2=1)
endif()
