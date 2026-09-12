# runtime — the shippable engine executable (composition root).

add_executable(runtime src/main.cpp)

target_link_libraries(runtime
    PRIVATE
        engine_project_options
        engine::core
        engine::platform
        engine::rendering
        engine::resources
        engine::physics
        engine::audio
        engine::scripting
        engine::procgen
        engine::scene
)

set_target_properties(runtime PROPERTIES OUTPUT_NAME "GameEngineCore")

# Copy runtime assets next to the executable for local runs.
add_custom_command(TARGET runtime POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_directory
            ${CMAKE_SOURCE_DIR}/assets $<TARGET_FILE_DIR:runtime>/assets
    COMMENT "Staging assets/ next to the runtime"
)

install(TARGETS runtime RUNTIME DESTINATION bin)
install(DIRECTORY ${CMAKE_SOURCE_DIR}/assets DESTINATION bin)
