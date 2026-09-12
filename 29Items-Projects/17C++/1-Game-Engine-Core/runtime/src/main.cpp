/// @file main.cpp
/// @brief Engine composition root + main loop.
///
/// This is the ONE place allowed to wire concrete subsystems together. It owns the
/// lifetime of every system, drives the fixed-timestep simulation + render loop, and
/// shuts everything down in reverse order. `--smoke-test` runs a few headless frames
/// and exits 0 (used by CI to verify the whole stack boots).

#include "engine/audio/AudioEngine.hpp"
#include "engine/core/Log.hpp"
#include "engine/core/jobs/JobSystem.hpp"
#include "engine/physics/PhysicsWorld.hpp"
#include "engine/platform/Clock.hpp"
#include "engine/platform/Window.hpp"
#include "engine/procgen/TerrainGenerator.hpp"
#include "engine/procgen/TerrainModel.hpp"
#include "engine/rendering/Renderer.hpp"
#include "engine/resources/AssetManager.hpp"
#include "engine/scene/Scene.hpp"
#include "engine/scripting/ScriptEngine.hpp"

#include <string_view>

namespace {

bool hasFlag(int argc, char** argv, std::string_view flag) {
    for (int i = 1; i < argc; ++i) {
        if (flag == argv[i]) {
            return true;
        }
    }
    return false;
}

} // namespace

int main(int argc, char** argv) {
    using namespace engine;

    const bool smokeTest = hasFlag(argc, argv, "--smoke-test");

    log::init(log::Level::Debug);
    log::info("Game Engine Core starting (smoke-test={})", smokeTest);

    // --- Foundation -------------------------------------------------------
    jobs::JobSystem jobs;
    jobs.start();

    platform::Window window;
    auto             win = window.create({.title = "Game Engine Core", .vulkan = true});
    if (!win && !smokeTest) {
        log::warn("Window creation failed: {} (continuing headless)", win.error().message);
    }

    // --- Subsystems -------------------------------------------------------
    rendering::Renderer renderer;
    if (auto r = renderer.initialize(window, jobs); !r) {
        log::error("Renderer init failed: {}", r.error().message);
    }

    audio::AudioEngine audio;
    (void) audio.initialize();

    scene::Scene  scene("Main");
    physics::PhysicsWorld physics;

    scripting::ScriptEngine scripts;
    (void) scripts.initialize(scene.registry(), /*sandboxed*/ true);

    resources::AssetManager assets(jobs);

    // --- Procedural terrain ----------------------------------------------
    procgen::TerrainModel terrainModel;
    (void) terrainModel.load("models/terrain_model.onnx", "v1");
    procgen::TerrainGenerator terrain(jobs, terrainModel);
    // NOTE: this callback fires on a worker thread. In production, push results to a
    // thread-safe queue and drain it on the main thread before touching the Registry
    // (the ECS is not internally synchronized). Inlined here only to show the flow.
    terrain.streamAround(/*seed*/ 1337, math::Vec3{0, 0, 0}, /*radius*/ 1, "grassland",
                         [&](procgen::TerrainTile&& tile) {
                             const auto e = scene.registry().create();
                             scene.registry().emplace<Transform>(e);
                             scene.registry().emplace<scene::TerrainTileTag>(
                                 e, scene::TerrainTileTag{tile.tileX, tile.tileY});
                         });

    // --- Demo content -----------------------------------------------------
    const auto cube = scene.spawn("Cube");
    scene.registry().emplace<physics::RigidBody>(cube, physics::RigidBody{{0, 0, 0}, 1.0f});
    (void) assets.load("meshes/cube.mesh", resources::AssetType::Mesh);
    (void) renderer.loadRenderableAsync("meshes/cube.mesh");

    // --- Main loop --------------------------------------------------------
    platform::Clock         clock;
    platform::FixedTimestep fixed(1.0f / 60.0f);
    const u64               maxFrames = smokeTest ? 5 : 0; // 0 == run until quit

    u64 frame = 0;
    while (true) {
        const f32 dt = clock.tick();
        window.pollEvents();
        if (window.shouldClose()) {
            break;
        }

        // Fixed-step simulation (physics + scripts).
        const u32 steps = fixed.consume(dt);
        for (u32 s = 0; s < steps; ++s) {
            physics.step(scene.registry(), fixed.step());
            scripts.callOnUpdate(fixed.step());
        }

        audio.update(dt);
        renderer.renderFrame();

        ++frame;
        if (maxFrames != 0 && frame >= maxFrames) {
            log::info("Smoke test reached {} frames — exiting.", frame);
            break;
        }
    }

    // --- Shutdown (reverse order) ----------------------------------------
    jobs.waitForIdle();
    scripts.shutdown();
    audio.shutdown();
    renderer.shutdown();
    jobs.stop();
    window.destroy();
    log::info("Game Engine Core stopped after {} frame(s).", frame);
    log::shutdown();
    return 0;
}
