#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"
#include "engine/rendering/rhi/RHIDevice.hpp"
#include "engine/rendering/rhi/Swapchain.hpp"

#include <atomic>
#include <memory>
#include <string>

/// @file Renderer.hpp
/// @brief High-level rendering facade — the engine's "presentation layer".
///
/// This is the engine-domain analogue of a frontend component: it (1) "fetches data"
/// by asynchronously loading GPU resources, (2) exposes explicit loading / ready /
/// error **states**, and (3) "displays" by drawing each frame. Gameplay code talks to
/// this, never to Vulkan directly.

namespace engine::platform {
class Window;
}
namespace engine::jobs {
class JobSystem;
}

namespace engine::rendering {

/// Lifecycle state of the renderer (the "loading/error states" pattern).
enum class RendererState : u8 {
    Uninitialized,
    Initializing,
    Ready,
    DeviceLost,
    Failed,
};

/// Per-resource load state, mirroring a frontend's loading/loaded/error UX.
enum class ResourceState : u8 { Idle, Loading, Loaded, Errored };

/// Handle to an async-loaded renderable (e.g., a mesh + material).
struct RenderableHandle {
    u32 id = 0xFFFF'FFFFu;
    [[nodiscard]] bool valid() const noexcept { return id != 0xFFFF'FFFFu; }
};

class Renderer {
public:
    Renderer();   // defined in .cpp (pimpl: Impl is incomplete here)
    ~Renderer();

    Renderer(const Renderer&)            = delete;
    Renderer& operator=(const Renderer&) = delete;

    /// Initialize against a window + backend. Transitions Uninitialized -> Ready/Failed.
    [[nodiscard]] Result<bool> initialize(platform::Window&  window,
                                          jobs::JobSystem&   jobs,
                                          rhi::BackendType   backend = rhi::BackendType::Vulkan);
    void shutdown();

    [[nodiscard]] RendererState state() const noexcept { return state_.load(); }

    /// "Data fetching": kick off an async load of a renderable from an asset path.
    /// Returns immediately; the resource reports Loading -> Loaded/Errored later.
    [[nodiscard]] RenderableHandle loadRenderableAsync(std::string assetPath);

    /// Query the async load state for a renderable.
    [[nodiscard]] ResourceState resourceState(RenderableHandle handle) const;

    /// "Display": record + submit one frame. Skips drawing gracefully when not Ready,
    /// and substitutes a magenta placeholder for any renderable still loading/errored.
    void renderFrame();

    void onResize(u32 width, u32 height);

    [[nodiscard]] rhi::RHIDevice* device() const noexcept { return device_.get(); }

private:
    void transition(RendererState next);

    std::atomic<RendererState>      state_{RendererState::Uninitialized};
    std::unique_ptr<rhi::RHIDevice> device_;
    platform::Window*               window_ = nullptr;
    jobs::JobSystem*                jobs_   = nullptr;

    struct Impl;
    std::unique_ptr<Impl> impl_; // resource tables / render graph (pimpl)
};

} // namespace engine::rendering
