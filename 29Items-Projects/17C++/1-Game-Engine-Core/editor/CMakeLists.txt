# editor — ImGui-based in-engine tooling (optional; ENGINE_BUILD_EDITOR).

add_executable(editor src/EditorApp.cpp)

target_link_libraries(editor
    PRIVATE
        engine_project_options
        engine::core
        engine::scene
        engine::rendering
        engine::platform
)

set_target_properties(editor PROPERTIES OUTPUT_NAME "GameEngineEditor")

if(TARGET imgui::imgui)
    target_link_libraries(editor PRIVATE imgui::imgui)
    target_compile_definitions(editor PRIVATE ENGINE_HAS_IMGUI=1)
else()
    message(WARNING "[editor] ImGui not found — editor builds as a headless stub.")
endif()
