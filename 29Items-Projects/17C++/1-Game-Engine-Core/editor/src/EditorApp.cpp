/// @file EditorApp.cpp
/// @brief In-engine editor (ImGui) entry point — stub.
///
/// Reuses the runtime's subsystems and overlays editor UI (scene hierarchy, entity
/// inspector, profiler). Built only when ENGINE_BUILD_EDITOR is ON. Production wires
/// the ImGui SDL2 + Vulkan backends to the engine's Window/RHIDevice.

#include "engine/core/Log.hpp"
#include "engine/core/ecs/Registry.hpp"
#include "engine/scene/Components.hpp"
#include "engine/scene/Scene.hpp"

#if defined(ENGINE_HAS_IMGUI)
    #include <imgui.h>
#endif

namespace engine::editor {

/// Draw the entity hierarchy + inspector for the given scene.
void drawSceneHierarchy(scene::Scene& scene) {
#if defined(ENGINE_HAS_IMGUI)
    ImGui::Begin("Hierarchy");
    auto& reg = scene.registry();
    reg.view<Name>().each([&](ecs::Entity e, Name& name) {
        ImGui::PushID(static_cast<int>(e.id));
        if (ImGui::Selectable(name.value.c_str())) {
            // TODO: set selection -> inspector.
        }
        ImGui::PopID();
    });
    ImGui::End();

    ImGui::Begin("Inspector");
    // TODO: reflect the selected entity's components (Transform, MeshRenderer, ...).
    ImGui::End();
#else
    log::info("[Editor] hierarchy: '{}' has {} entities (ImGui not compiled in)",
              scene.name(), scene.registry().aliveCount());
#endif
}

} // namespace engine::editor

int main(int /*argc*/, char** /*argv*/) {
    using namespace engine;
    log::init(log::Level::Debug);
    log::info("Game Engine Core — Editor (stub)");

    scene::Scene scene("Editor Scene");
    scene.spawn("Camera");
    scene.spawn("Directional Light");
    scene.spawn("Player");

    // TODO: init Window + Renderer + ImGui backends, then run the editor loop:
    //   poll events -> ImGui::NewFrame -> drawSceneHierarchy(scene) -> render.
    editor::drawSceneHierarchy(scene);

    log::shutdown();
    return 0;
}
